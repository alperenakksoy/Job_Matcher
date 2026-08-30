"""
The protoc-generated job_matcher_pb2_grpc.py uses a flat import
(`import job_matcher_pb2 as job__matcher__pb2`) rather than a relative
one - this is standard behaviour for the Python gRPC codegen, not a bug
in our proto. We fix it up here by putting this package's directory on
sys.path before importing the generated modules, so the rest of the app
can just do:

    from app.generated import job_matcher_pb2, job_matcher_pb2_grpc

without every caller needing to know about this quirk.
"""

import os
import sys

_GENERATED_DIR = os.path.dirname(os.path.abspath(__file__))
if _GENERATED_DIR not in sys.path:
    sys.path.insert(0, _GENERATED_DIR)

from app.generated import job_matcher_pb2  # noqa: E402,F401
from app.generated import job_matcher_pb2_grpc  # noqa: E402,F401
