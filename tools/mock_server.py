#!/usr/bin/env python3
"""Mock Xtream Codes panel + M3U host for NovaPlay e2e testing.

Serves on 0.0.0.0:8899 (emulator reaches it via 10.0.2.2:8899).
- /player_api.php           Xtream API (user info, categories, streams, details)
- /live/u/p/{id}.m3u8       404 on purpose -> exercises HLS->TS fallback
- /live/u/p/{id}.ts         test MP4 bytes (ExoPlayer sniffs MP4 fine)
- /movie|series/u/p/{id}.*  test MP4
- /list.m3u                 M3U playlist with direct URLs
- /direct/{n}.mp4           test MP4
Supports HTTP Range so ExoPlayer can seek.
"""
import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.path.join(HERE, "test.mp4")

LIVE_CATEGORIES = [
    {"category_id": "10", "category_name": "News"},
    {"category_id": "20", "category_name": "Sports"},
    {"category_id": "30", "category_name": "Maroc"},
]

LIVE_STREAMS = [
    {"stream_id": 101, "num": 1, "name": "France 24", "category_id": "10", "stream_icon": ""},
    {"stream_id": 102, "num": "2", "name": "BBC World News", "category_id": "10", "stream_icon": ""},
    {"stream_id": 103, "num": 3, "name": "Al Jazeera", "category_id": "10", "stream_icon": ""},
    {"stream_id": 201, "num": 4, "name": "Bein Sports 1", "category_id": "20", "stream_icon": ""},
    {"stream_id": 202, "num": 5, "name": "Eurosport", "category_id": "20", "stream_icon": ""},
    {"stream_id": 301, "num": 6, "name": "Télé Maroc", "category_id": "30", "stream_icon": ""},
    {"stream_id": 302, "num": 7, "name": "2M Monde", "category_id": "30", "stream_icon": ""},
    # dirty entry: no name -> must be skipped, never fatal
    {"stream_id": 999, "num": 99, "category_id": "30"},
]

VOD_CATEGORIES = [
    {"category_id": "40", "category_name": "Action"},
    {"category_id": "50", "category_name": "Drama"},
]

VOD_STREAMS = [
    {"stream_id": 401, "name": "The Batman", "category_id": "40", "stream_icon": "",
     "rating": "8.2", "container_extension": "mp4", "year": "2022"},
    {"stream_id": 402, "name": "Batman Begins", "category_id": "40", "stream_icon": "",
     "rating": 8.4, "container_extension": "mp4", "year": "2005"},
    {"stream_id": 403, "name": "Heat", "category_id": "40", "stream_icon": "",
     "rating": "8.7", "container_extension": "mp4", "year": "1995"},
    {"stream_id": 501, "name": "The Godfather", "category_id": "50", "stream_icon": "",
     "rating": "9.2", "container_extension": "mp4", "year": "1972"},
]

SERIES_CATEGORIES = [{"category_id": "60", "category_name": "Thriller"}]

SERIES_LIST = [
    {"series_id": 601, "name": "Breaking Code", "category_id": "60", "cover": "",
     "plot": "A programmer turns to a life of syntax crime.", "rating": "9.0",
     "release_date": "2021-01-15", "backdrop_path": [""]},
]

SERIES_INFO = {
    "info": {"plot": "A programmer turns to a life of syntax crime.",
             "rating": "9.0", "release_date": "2021-01-15", "backdrop_path": ""},
    "episodes": {
        "1": [
            {"id": "7001", "episode_num": 1, "title": "Hello World", "season": 1,
             "container_extension": "mp4", "info": {"duration_secs": 12}},
            {"id": "7002", "episode_num": 2, "title": "Null Pointer", "season": 1,
             "container_extension": "mp4", "info": {"duration_secs": 12}},
        ],
        "2": [
            {"id": "7003", "episode_num": 1, "title": "Race Condition", "season": 2,
             "container_extension": "mp4", "info": {"duration_secs": 12}},
        ],
    },
}

M3U = """#EXTM3U
#EXTINF:-1 tvg-id="d1" tvg-name="Direct One" tvg-logo="" group-title="Direct",Direct One
http://10.0.2.2:8899/direct/1.mp4
#EXTINF:-1 tvg-id="d2" tvg-name="Direct Two" tvg-logo="" group-title="Direct",Direct Two
http://10.0.2.2:8899/direct/2.mp4
#EXTINF:-1 tvg-id="d3" tvg-name="Chaîne Française" group-title="Français",Chaîne Française
http://10.0.2.2:8899/direct/3.mp4
"""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)

    def send_json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_media(self):
        size = os.path.getsize(MEDIA)
        range_header = self.headers.get("Range")
        start, end = 0, size - 1
        status = 200
        if range_header:
            m = re.match(r"bytes=(\d*)-(\d*)", range_header)
            if m:
                if m.group(1):
                    start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
                status = 206
        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Accept-Ranges", "bytes")
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(length))
        self.end_headers()
        with open(MEDIA, "rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(65536, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        action = (query.get("action") or [""])[0]

        if path == "/player_api.php":
            if not action:
                self.send_json({
                    "user_info": {"status": "Active",
                                  "exp_date": str(int(time.time()) + 180 * 86400),
                                  "max_connections": "2"},
                    "server_info": {"url": "10.0.2.2", "port": "8899"},
                })
            elif action == "get_live_categories":
                self.send_json(LIVE_CATEGORIES)
            elif action == "get_live_streams":
                self.send_json(LIVE_STREAMS)
            elif action == "get_vod_categories":
                self.send_json(VOD_CATEGORIES)
            elif action == "get_vod_streams":
                self.send_json(VOD_STREAMS)
            elif action == "get_series_categories":
                self.send_json(SERIES_CATEGORIES)
            elif action == "get_series":
                self.send_json(SERIES_LIST)
            elif action == "get_series_info":
                self.send_json(SERIES_INFO)
            elif action == "get_vod_info":
                self.send_json({"info": {
                    "plot": "A gritty test clip stands in for a feature film.",
                    "genre": "Action, Test",
                    "duration_secs": "12",
                    "releasedate": "2022-03-01",
                    "rating": "8.2",
                    "backdrop_path": [""],
                }})
            else:
                self.send_json([])
        elif path == "/list.m3u":
            body = M3U.encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/x-mpegurl")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif path.endswith(".m3u8"):
            # No HLS here: force the player's HLS -> TS fallback path.
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
        elif path.startswith(("/live/", "/movie/", "/series/", "/direct/")):
            self.send_media()
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()


if __name__ == "__main__":
    print("mock xtream server on :8899", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8899), Handler).serve_forever()
