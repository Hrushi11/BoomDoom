package app.boomdoom.bench;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Standalone device benchmark. All inputs are team fixtures copied over ADB. */
public class ReelBenchmark {
    private static final int SIDE = 224;

    private static double elapsedMs(long start) {
        return (SystemClock.elapsedRealtimeNanos() - start) / 1e6;
    }

    private static float[] caption(OrtEnvironment env, OrtSession session, OrtSession tokenizer, JSONObject meta)
            throws Exception {
        int[] pieces;
        try (OnnxTensor text = OnnxTensor.createTensor(env, new String[]{meta.getString("caption")});
             OrtSession.Result result = tokenizer.run(Collections.singletonMap("text", text))) {
            pieces = (int[]) result.get(0).getValue();
        }
        JSONArray hypotheses = meta.getJSONArray("hypothesis_ids");
        int width = 0;
        for (int i = 0; i < hypotheses.length(); i++) {
            width = Math.max(width, Math.min(256, pieces.length + hypotheses.getJSONArray(i).length() + 4));
        }
        long[][] ids = new long[hypotheses.length()][width];
        long[][] mask = new long[hypotheses.length()][width];
        for (int row = 0; row < hypotheses.length(); row++) {
            JSONArray hypothesis = hypotheses.getJSONArray(row);
            // XLM-R pairs: <s> caption </s></s> hypothesis </s>. Pad with id 1.
            int count = Math.min(pieces.length, width - hypothesis.length() - 4);
            int pos = 1;
            for (int i = 0; i < count; i++) ids[row][pos++] = pieces[i];
            ids[row][pos++] = 2;
            ids[row][pos++] = 2;
            for (int i = 0; i < hypothesis.length(); i++) ids[row][pos++] = hypothesis.getInt(i);
            ids[row][pos++] = 2;
            Arrays.fill(mask[row], 0, pos, 1L);
            Arrays.fill(ids[row], pos, width, 1L);
        }
        try (OnnxTensor tokens = OnnxTensor.createTensor(env, ids);
             OnnxTensor attention = OnnxTensor.createTensor(env, mask)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", tokens);
            inputs.put("attention_mask", attention);
            try (OrtSession.Result result = session.run(inputs)) {
                return (float[]) result.get(0).getValue();
            }
        }
    }

    private static float[] pixels(Bitmap image) {
        Bitmap square = Bitmap.createScaledBitmap(image, SIDE, SIDE, true);
        int plane = SIDE * SIDE;
        int[] rgb = new int[plane];
        square.getPixels(rgb, 0, SIDE, 0, 0, SIDE, SIDE);
        if (square != image) square.recycle();
        float[] data = new float[plane * 3];
        for (int i = 0; i < plane; i++) {
            data[i] = ((rgb[i] >> 16) & 255) / 127.5f - 1f;
            data[i + plane] = ((rgb[i] >> 8) & 255) / 127.5f - 1f;
            data[i + 2 * plane] = (rgb[i] & 255) / 127.5f - 1f;
        }
        return data;
    }

    private static float[] scoreImage(OrtEnvironment env, OrtSession session, Bitmap image) throws Exception {
        float[] data = pixels(image);
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[]{1, 3, SIDE, SIDE});
             OrtSession.Result result = session.run(Collections.singletonMap("pixels", input))) {
            return ((float[][]) result.get(0).getValue())[0];
        }
    }

    private static float[] readerFrame(OrtEnvironment env, OrtSession session, ImageReader reader, Bitmap fixture)
            throws Exception {
        Canvas canvas = reader.getSurface().lockCanvas(null);
        try {
            canvas.drawBitmap(fixture, null, new Rect(0, 0, reader.getWidth(), reader.getHeight()), null);
        } finally {
            reader.getSurface().unlockCanvasAndPost(canvas);
        }
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) throw new IllegalStateException("fixture frame unavailable");
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) throw new IllegalStateException("expected RGBA_8888");
            ByteBuffer source = plane.getBuffer();
            int rowBytes = image.getWidth() * 4;
            ByteBuffer packed = ByteBuffer.allocateDirect(rowBytes * image.getHeight());
            for (int row = 0; row < image.getHeight(); row++) {
                int offset = row * plane.getRowStride();
                source.limit(offset + rowBytes);
                source.position(offset);
                packed.put(source);
            }
            packed.flip();
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                bitmap.copyPixelsFromBuffer(packed);
                return scoreImage(env, session, bitmap);
            } finally {
                bitmap.recycle();
            }
        }
    }

    private static float[] frames(OrtEnvironment env, OrtSession session, String path, int count, int labelCount)
            throws Exception {
        float[] mean = new float[labelCount];
        int used = 0;
        try (RandomAccessFile file = new RandomAccessFile(path, "r");
             MediaMetadataRetriever video = new MediaMetadataRetriever()) {
            video.setDataSource(new MediaDataSource() {
                public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
                    file.seek(position);
                    return file.read(buffer, offset, size);
                }
                public long getSize() throws IOException { return file.length(); }
                public void close() { /* The enclosing block owns the file. */ }
            });
            long durationMs = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            for (int i = 0; i < count && 2000L * i < durationMs; i++) {
                Bitmap image = video.getScaledFrameAtTime(i * 2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST, SIDE, SIDE);
                if (image == null) continue;
                try {
                    float[] scores = scoreImage(env, session, image);
                    for (int j = 0; j < labelCount; j++) mean[j] += scores[j];
                    used++;
                } finally {
                    image.recycle();
                }
            }
        }
        if (used == 0) throw new IllegalStateException("fixture has no decodable frame");
        for (int j = 0; j < mean.length; j++) mean[j] /= used;
        return mean;
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        String stage = args[1];
        boolean text = stage.equals("caption");
        int repeats = Integer.parseInt(args[2]);
        long start = SystemClock.elapsedRealtimeNanos();
        String pack = text ? "caption" : "vision";
        JSONObject metadata = new JSONObject(new String(Files.readAllBytes(Paths.get(dir, pack + ".json")), "UTF-8"));
        JSONArray labels = metadata.getJSONArray("labels");
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        env.setTelemetry(false);
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(4);
            if (text) options.registerCustomOpLibrary(dir + "/libortextensions.so");
            try (OrtSession session = env.createSession(dir + "/" + pack + ".onnx", options);
                 OrtSession tokenizer = text ? env.createSession(dir + "/tokenizer.onnx", options) : null;
                 ImageReader reader = stage.equals("image_reader")
                         ? ImageReader.newInstance(448, 800, PixelFormat.RGBA_8888, 2) : null) {
                Bitmap fixture = reader == null ? null : android.graphics.BitmapFactory.decodeFile(dir + "/frame.png");
                System.out.println(new JSONObject().put("stage", stage).put("startup_ms", elapsedMs(start)));
                for (int run = 1; run <= repeats; run++) {
                    start = SystemClock.elapsedRealtimeNanos();
                    float[] scores;
                    if (text) {
                        scores = caption(env, session, tokenizer, metadata);
                    } else if (reader != null) {
                        scores = Arrays.copyOf(readerFrame(env, session, reader, fixture), labels.length() - 1);
                    } else {
                        scores = frames(env, session, dir + "/sample.mp4", stage.equals("one_frame") ? 1 : 3, labels.length() - 1);
                    }
                    int best = 0;
                    for (int i = 1; i < scores.length; i++) if (scores[i] > scores[best]) best = i;
                    String label = scores[best] >= metadata.getDouble("other_threshold") ? labels.getString(best) : "other";
                    System.out.println(new JSONObject().put("stage", stage).put("run", run).put("label", label)
                            .put("confidence", (double) scores[best]).put("ms", elapsedMs(start)));
                }
            }
        }
    }
}
