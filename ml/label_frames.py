"""Build the labelled frame set from team recordings; OpenAI transcribes and suggests labels; humans confirm.

Tech Spec §9.6. Team-only tooling; never ships in the app.
"""

import argparse


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    raise SystemExit("TODO: not implemented yet")


if __name__ == "__main__":
    main()
