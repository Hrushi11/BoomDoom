"""CloudWatch metrics via Embedded Metric Format: one JSON log line per metric, no API calls.

CloudWatch Logs extracts these automatically on AWS; locally they are just log lines.
"""

import json
import sys
import time

NAMESPACE = "BoomDoom"


def emit(name: str, value: float, unit: str = "Count", **dimensions: str) -> None:
    from app.config import get_settings

    dims = {"env": get_settings().boomdoom_env, **dimensions}
    payload = {
        "_aws": {
            "Timestamp": int(time.time() * 1000),
            "CloudWatchMetrics": [
                {"Namespace": NAMESPACE, "Dimensions": [list(dims)], "Metrics": [{"Name": name, "Unit": unit}]}
            ],
        },
        name: value,
        **dims,
    }
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()
