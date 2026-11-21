"""Seekable, in-memory HTTP range input for the team-only media benchmark."""

import io
import re
from collections import OrderedDict
from time import perf_counter
from urllib.parse import urlsplit

import requests


class RangePreview(io.RawIOBase):
    """Fetch decoder-requested ranges, including an MP4 index at the end if necessary."""

    block_size = 256 * 1024

    def __init__(self, url):
        super().__init__()
        if urlsplit(url).scheme != "https":
            raise ValueError("a direct HTTPS media URL is required")
        self.url = url
        self.session = requests.Session()
        self.cache = OrderedDict()
        self.size = None
        self.position = 0
        self.etag = None
        self.bytes_fetched = 0
        self.fetch_ms = 0.0

    def _block(self, offset):
        if offset in self.cache:
            self.cache.move_to_end(offset)
            return self.cache[offset]
        start = perf_counter()
        headers = {"Range": f"bytes={offset}-{offset + self.block_size - 1}", "Accept-Encoding": "identity"}
        if self.etag:
            headers["If-Range"] = self.etag
        with self.session.get(self.url, headers=headers, timeout=(5, 10), stream=True) as response:
            response.raise_for_status()
            match = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)", response.headers.get("Content-Range", ""))
            if response.status_code != 206 or not match:
                raise OSError("server did not return a byte range; use a local file or a range-enabled media URL")
            first, last, size = map(int, match.groups())
            if (
                first != offset
                or last != min(offset + self.block_size, size) - 1
                or size <= first
                or self.size not in (None, size)
            ):
                raise OSError("inconsistent media byte range")
            data = response.raw.read(last - first + 1)
            if len(data) != last - first + 1:
                raise OSError("incomplete media byte range")
            self.etag = response.headers.get("ETag")
            self.size = size
        self.fetch_ms += (perf_counter() - start) * 1000
        self.bytes_fetched += len(data)
        self.cache[offset] = data
        if len(self.cache) > 32:
            self.cache.popitem(last=False)
        return data

    def seekable(self):
        return True

    def readable(self):
        return True

    def tell(self):
        return self.position

    def seek(self, offset, whence=io.SEEK_SET):
        if whence == io.SEEK_END:
            if self.size is None:
                self._block(0)
            offset += self.size
        elif whence == io.SEEK_CUR:
            offset += self.position
        elif whence != io.SEEK_SET:
            raise ValueError("unsupported seek mode")
        if offset < 0:
            raise ValueError("negative media offset")
        self.position = offset
        return offset

    def readinto(self, buffer):
        if not len(buffer) or (self.size is not None and self.position >= self.size):
            return 0
        offset = self.position // self.block_size * self.block_size
        block = self._block(offset)
        start = self.position - offset
        count = min(len(buffer), len(block) - start)
        buffer[:count] = block[start : start + count]
        self.position += count
        return count

    def close(self):
        self.session.close()
        self.cache.clear()
        super().close()
