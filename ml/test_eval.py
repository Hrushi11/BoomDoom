import json
import tempfile
import unittest
from pathlib import Path

from eval import metrics, read_set


class EvaluationTest(unittest.TestCase):
    def test_counts_are_per_reel_and_missing_flag_precision_is_undefined(self):
        sample = {"label": "other", "flagged": False, "timing_ms": {"frames": 1}, "total_ms": 1}
        records = [("food", sample), ("other", sample)]
        result = metrics(records, {"rage_bait"})
        self.assertEqual(0.5, result["accuracy"])
        self.assertEqual(0, result["recall_by_label"]["food"])
        self.assertIsNone(result["flag_precision"])
        self.assertEqual(2, result["timing_by_path_including_first_use"]["frames"]["n"])

    def test_rejects_duplicate_reels_and_unknown_ground_truth(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "set.jsonl"
            row = json.dumps({"id": "1", "label": "food"})
            path.write_text(row + "\n" + row)
            with self.assertRaisesRegex(ValueError, "duplicate"):
                list(read_set(path, {"food"}))
            with self.assertRaisesRegex(ValueError, "unknown"):
                list(read_set(path, {"finance"}))
