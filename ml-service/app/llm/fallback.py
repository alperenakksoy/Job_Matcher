import logging

from pydantic import BaseModel

from app.llm.provider import LLMCallResult, LLMProvider, LLMProviderError, T

logger = logging.getLogger(__name__)


class AllProvidersFailedError(Exception):

    def __init__(self, attempts: list[tuple[str, Exception]]):
        self.attempts = attempts
        summary = "; ".join(f"{name}: {err}" for name, err in attempts)
        super().__init__(f"All LLM providers failed - {summary}")


class FallbackLLMProvider:

    def __init__(self, providers: list[LLMProvider]):
        if not providers:
            raise ValueError("FallbackLLMProvider needs at least one provider")
        self._providers = providers

    def complete(self, prompt: str, response_model: type[T]) -> tuple[LLMCallResult, str]:
        """Returns (result, provider_name_that_succeeded)."""
        attempts: list[tuple[str, Exception]] = []

        for provider in self._providers:
            try:
                result = provider.complete(prompt, response_model)
                if attempts:
                    logger.info(
                        "Provider %s succeeded after %d prior failure(s): %s",
                        provider.name, len(attempts),
                        ", ".join(name for name, _ in attempts),
                    )
                return result, provider.name
            except LLMProviderError as e:
                attempts.append((provider.name, e))
                if not e.is_retryable:
                    logger.error(
                        "Provider %s failed with a non-retryable error, "
                        "not falling back: %s", provider.name, e,
                    )
                    raise
                logger.warning(
                    "Provider %s failed (retryable), trying next: %s",
                    provider.name, e,
                )

        raise AllProvidersFailedError(attempts)
