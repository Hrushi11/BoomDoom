"""Train logistic heads on SigLIP 2 and EmbeddingGemma embeddings; export weights as head_v{n}.bin.

Tech Spec §9.6. Team-only tooling; never ships in the app.
"""

import argparse


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    raise SystemExit("TODO: not implemented yet")


if __name__ == "__main__":
    main()
