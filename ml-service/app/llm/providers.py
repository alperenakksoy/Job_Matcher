"""Concrete LLMProvider implementations.

Both use instructor's unified `from_provider("vendor/model")` API rather
than each vendor's raw SDK client - this is the same interaction pattern
instructor documents for every backend, so swapping/adding a provider
later (e.g. an OpenAI fallback) means one new class shaped exactly like
these two, not a new integration pattern.

API keys are read from the environment (GOOGLE_API_KEY, GROQ_API_KEY) by
instructor/the underlying SDKs themselves - not read explicitly here, so
we never hold a key in a Python variable longer than necessary.
"""

import logging
import time

import instructor
from pydantic import BaseModel

from app.llm.provider import LLMCallResult, LLMProvider, LLMProviderError, T

logger = logging.getLogger(__name__)

# Quota/rate-limit errors differ by SDK; instructor doesn't normalize these
# into a single exception type, so each provider maps its own SDK's
# exceptions. We only need to distinguish "worth trying the next provider"
# from "will fail the same way everywhere" (e.g. a malformed prompt).
_RETRYABLE_ERROR_MARKERS = (
    "rate limit", "rate_limit", "quota", "resource_exhausted",
    "429", "503", "overloaded", "timeout", "unavailable",
)


def _is_retryable(exc: Exception) -> bool:
    text = str(exc).lower()
    return any(marker in text for marker in _RETRYABLE_ERROR_MARKERS)


class GeminiProvider(LLMProvider):
    name = "gemini"

    def __init__(self, model: str = "gemini-3.8-flash"):
        self.model = model
        # instructor.from_provider builds the underlying google-genai client
        # internally and reads GOOGLE_API_KEY from the environment.
        self._client = instructor.from_provider(f"google/{model}")

    def complete(self, prompt: str, response_model: type[T]) -> LLMCallResult:
        start = time.monotonic()
        try:
            parsed, raw = self._client.chat.completions.create_with_completion(
                response_model=response_model,
                messages=[{"role": "user", "content": prompt}],
            )
        except Exception as e:
            raise LLMProviderError(
                f"Gemini call failed: {e}", is_retryable=_is_retryable(e)
            ) from e

        duration_ms = int((time.monotonic() - start) * 1000)
        usage = getattr(raw, "usage_metadata", None)
        prompt_tokens = getattr(usage, "prompt_token_count", None) if usage else None
        completion_tokens = getattr(usage, "candidates_token_count", None) if usage else None

        return LLMCallResult(
            parsed=parsed,
            model=self.model,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            duration_ms=duration_ms,
        )


class GroqProvider(LLMProvider):
    name = "groq"

    def __init__(self, model: str = "openai/gpt-oss-120b"):
        self.model = model
        self._client = instructor.from_provider(f"groq/{model}")

    def complete(self, prompt: str, response_model: type[T]) -> LLMCallResult:
        start = time.monotonic()
        try:
            parsed, raw = self._client.chat.completions.create_with_completion(
                response_model=response_model,
                messages=[{"role": "user", "content": prompt}],
            )
        except Exception as e:
            raise LLMProviderError(
                f"Groq call failed: {e}", is_retryable=_is_retryable(e)
            ) from e

        duration_ms = int((time.monotonic() - start) * 1000)
        usage = getattr(raw, "usage", None)
        prompt_tokens = getattr(usage, "prompt_tokens", None) if usage else None
        completion_tokens = getattr(usage, "completion_tokens", None) if usage else None

        return LLMCallResult(
            parsed=parsed,
            model=self.model,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            duration_ms=duration_ms,
        )