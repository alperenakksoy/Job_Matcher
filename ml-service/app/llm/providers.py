import logging
import time

import instructor
from pydantic import BaseModel

from app.llm.provider import LLMCallResult, LLMProvider, LLMProviderError, T

logger = logging.getLogger(__name__)

_RETRYABLE_ERROR_MARKERS = (
    "rate limit", "rate_limit", "quota", "resource_exhausted",
    "429", "503", "overloaded", "timeout", "unavailable",
)


def _is_retryable(exc: Exception) -> bool:
    text = str(exc).lower()
    return any(marker in text for marker in _RETRYABLE_ERROR_MARKERS)


class GeminiProvider(LLMProvider):
    name = "gemini"

    def __init__(self, model: str = "gemini-2.0-flash"):
        self.model = model
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

    def __init__(self, model: str = "llama-3.3-70b-versatile"):
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
