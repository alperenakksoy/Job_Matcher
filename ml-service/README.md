# Job Matcher ML Service

FastAPI (health/debug) + gRPC (real inter-service traffic with Spring),
per the project plan. This week (Week 3): deterministic resume parsing
only - no LLM calls yet.

## Setup

```bash
cd ml-service
uv venv
source .venv/bin/activate
uv pip install -e ".[dev]"

# spaCy language model - optional but recommended (name detection quality
# is lower without it; the parser falls back to a blank tokenizer if this
# isn't installed, it won't crash).
python -m spacy download en_core_web_sm
```

## Generate gRPC stubs

The generated files in `app/generated/` are committed for convenience but
regenerate them whenever `proto/job_matcher.proto` (at the repo root)
changes:

```bash
./scripts/generate_proto.sh
```

## Run

```bash
uvicorn app.main:app --reload --port 8000
```

This starts both:
- REST health check: `GET http://localhost:8000/health`
- gRPC server: `localhost:50051`

## Test

```bash
pytest tests/ -v
```

## Known limitations (by design, this week)

- `ParseJob` and `ComputeMatch` are skeleton RPCs - they return
  `success=false` with a clear error message. Real implementations land
  in Week 4 (ParseJob) and Week 5 (ComputeMatch).
- Work experience / education line-splitting (company vs. title vs.
  description) is a best-effort heuristic with deliberately low
  confidence scores. This is intentional - Week 4's LLM completion pass
  is meant to fix exactly these low-confidence fields, not the parser
  guessing harder with more fragile regex.
- Scanned/image-only PDFs (no extractable text layer) are rejected with
  a clear error rather than silently returning an empty parse. OCR is
  out of scope for this week.
