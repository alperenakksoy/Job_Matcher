"""Entrypoint for the ML service.

Per the plan: FastAPI serves health/debug endpoints over REST, gRPC
handles the actual inter-service communication with Spring. Both run in
the same process - the gRPC server runs in a background thread started
from FastAPI's lifespan, so `uvicorn app.main:app` is the single command
that starts everything.
"""

import logging
from concurrent import futures
from contextlib import asynccontextmanager

import grpc
from fastapi import FastAPI

from app.generated import job_matcher_pb2_grpc as pb2_grpc
from app.grpc_server import JobMatcherMlServicer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GRPC_PORT = 50051

_grpc_server: grpc.Server | None = None


def _start_grpc_server() -> grpc.Server:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_JobMatcherMlServiceServicer_to_server(JobMatcherMlServicer(), server)
    server.add_insecure_port(f"[::]:{GRPC_PORT}")
    server.start()
    logger.info("gRPC server listening on port %d", GRPC_PORT)
    return server


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _grpc_server
    _grpc_server = _start_grpc_server()
    yield
    logger.info("Shutting down gRPC server")
    _grpc_server.stop(grace=5)


app = FastAPI(title="Job Matcher ML Service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "grpc_port": GRPC_PORT}
