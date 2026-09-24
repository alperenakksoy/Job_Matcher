from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


class LLMProviderError(Exception):
    def __init__(self, message: str, *, is_retryable: bool = True):
        super().__init__(message)
        self.is_retryable = is_retryable


@dataclass
class LLMCallResult:

    parsed: BaseModel
    model: str
    prompt_tokens: int | None
    completion_tokens: int | None
    duration_ms: int


class LLMProvider(ABC):
    name: str

    @abstractmethod
    def complete(self, prompt: str, response_model: type[T]) -> LLMCallResult:
        raise NotImplementedError
