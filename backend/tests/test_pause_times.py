from workers.render_worker.pause_times import interaction_pauses, segment_end_times

SEGMENTS = [
    {"id": "hook", "narration": "Waiters remember unpaid orders."},
    {"id": "setup", "narration": "Once paid, they forget."},
]


def _words(pairs):
    return [{"word": w, "start": e - 0.2, "end": e} for w, e in pairs]


def test_aligned_captions_use_last_word_end():
    words = _words(
        [
            ("Waiters", 0.4),
            ("remember", 0.9),
            ("unpaid", 1.3),
            ("orders.", 1.8),
            ("Once", 2.2),
            ("paid,", 2.5),
            ("they", 2.7),
            ("forget.", 3.2),
        ]
    )
    ends, fallback = segment_end_times(SEGMENTS, words, duration_s=3.5)
    assert not fallback
    assert ends == {"hook": 1.8, "setup": 3.2}
    pauses = interaction_pauses([{"id": "i1", "after_segment": "hook"}], ends)
    assert pauses[0]["pause_at_s"] == 1.95


def test_bad_alignment_falls_back_to_proportional():
    words = _words([("totally", 1.0), ("different", 2.0)])
    ends, fallback = segment_end_times(SEGMENTS, words, duration_s=8.0)
    assert fallback
    assert ends == {"hook": 4.0, "setup": 8.0}
