# Keep kotlinx.serialization models used by Retrofit
-keepclassmembers class app.boomdoom.** { *** Companion; }

# ONNX Runtime (on-device reel classification) is called from native code through JNI.
-keep class ai.onnxruntime.** { *; }
