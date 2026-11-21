import io
import unittest
from unittest.mock import MagicMock

from preview import RangePreview


class RangePreviewTest(unittest.TestCase):
    def setUp(self):
        self.source = RangePreview("https://example.org/fixture.mp4")
        self.addCleanup(self.source.close)
        self.source.block_size = 4
        self.source.session = MagicMock()
        self.source.session.get.side_effect = self.get_range
        self.ranges = []

    def get_range(self, url, headers, **kwargs):
        start, end = map(int, headers["Range"].removeprefix("bytes=").split("-"))
        payload = b"0123456789"
        end = min(end, len(payload) - 1)
        self.ranges.append((start, end))
        response = MagicMock()
        response.__enter__.return_value = response
        response.status_code = 206
        response.headers = {"Content-Range": f"bytes {start}-{end}/{len(payload)}", "ETag": '"example"'}
        response.raw = io.BytesIO(payload[start : end + 1])
        return response

    def test_seeking_to_index_and_rewinding_reuses_bytes(self):
        self.assertEqual(0, self.source.bytes_fetched)
        self.assertEqual(b"012", self.source.read(3))
        self.source.seek(-2, io.SEEK_END)
        self.assertEqual(b"89", self.source.read(2))
        self.assertEqual(b"", self.source.read(1))
        self.source.seek(0)
        self.assertEqual(b"0123", self.source.read(4))
        self.assertEqual([(0, 3), (8, 9)], self.ranges)
        self.assertEqual(6, self.source.bytes_fetched)

    def test_server_ignoring_range_does_not_trigger_full_download(self):
        self.source.session.get.side_effect = None
        response = self.source.session.get.return_value.__enter__.return_value
        response.status_code = 200
        response.headers = {}
        with self.assertRaises(OSError):
            self.source.read(1)
        response.raw.read.assert_not_called()
