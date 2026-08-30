#!/usr/bin/env bash
# Regenerates app/generated/*.py from the shared proto/job_matcher.proto.
# Run this from the ml-service/ directory whenever the proto changes.
#
# Usage: ./scripts/generate_proto.sh

set -euo pipefail

cd "$(dirname "$0")/.."

python -m grpc_tools.protoc \
  -I ../proto \
  --python_out=app/generated \
  --grpc_python_out=app/generated \
  --pyi_out=app/generated \
  ../proto/job_matcher.proto

echo "Generated stubs in app/generated/"
