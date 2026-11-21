# Offline ML tooling (Tech Spec §9.6)

Team-only scripts. Nothing here ships in the app, and no user data is ever used.

| Script | Purpose |
|---|---|
| `label_frames.py` | Build the labelled frame set from team screen recordings |
| `synth_text.py` | Generate Hindi/Hinglish/English caption/transcript examples per label |
| `train_heads.py` | Train logistic heads on SigLIP 2 / EmbeddingGemma embeddings |
| `export_onnx.py` | Export and int8-quantise SigLIP 2 and EmbeddingGemma |
| `eval.py` | 100-reel + 50-Hinglish-reel test sets: accuracy, confusion matrix, threshold sweeps |

Heavy deps (torch, transformers, onnx) live in `requirements.txt`, separate from the backend venv:

```bash
python3.11 -m venv ml/.venv && ml/.venv/bin/pip install -r ml/requirements.txt
```
