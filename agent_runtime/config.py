"""Agent Runtime 的统一配置入口。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os

from dotenv import dotenv_values


@dataclass(frozen=True, slots=True)
class ModelConfig:
    provider: str
    model: str
    endpoint: str
    api_key: str


@dataclass(frozen=True, slots=True)
class RuntimeSettings:
    milvus_uri: str
    milvus_collection_name: str
    embedding: ModelConfig
    chat: ModelConfig


# 各供应商的默认 base_url 和 model，方便用户只配 provider + api_key 即可使用。
# 用户仍可通过 ITOPS_CHAT_ENDPOINT / ITOPS_CHAT_MODEL 覆盖默认值。
CHAT_PROVIDER_DEFAULTS: dict[str, dict[str, str]] = {
    "deepseek": {
        "endpoint": "https://api.deepseek.com",
        "model": "deepseek-chat",
    },
    "qwen": {
        "endpoint": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
    },
    "glm": {
        "endpoint": "https://open.bigmodel.cn/api/paas/v4",
        "model": "glm-4-flash",
    },
    "openai": {
        "endpoint": "https://api.openai.com/v1",
        "model": "gpt-4o-mini",
    },
    "mock": {
        "endpoint": "",
        "model": "mock-chat",
    },
}

# Embedding 供应商默认配置。
# Ollama 本地部署 bge-m3，通过 OpenAI 兼容协议调用。
EMBEDDING_PROVIDER_DEFAULTS: dict[str, dict[str, str]] = {
    "ollama": {
        "endpoint": "http://localhost:11434/v1",
        "model": "bge-m3:latest",
        "api_key": "ollama",
    },
    "openai": {
        "endpoint": "https://api.openai.com/v1",
        "model": "text-embedding-3-small",
    },
}


_DEFAULT_DOTENV_PATHS = (
    Path(__file__).resolve().parents[1] / ".env",
    Path(__file__).resolve().parents[1] / ".env.local",
    Path(__file__).resolve().parent / ".env",
)


def _load_dotenv_values(dotenv_paths: tuple[Path, ...]) -> dict[str, str]:
    """
    使用 python-dotenv 读取多份 .env 文件。
    后面的文件会覆盖前面的同名键，再由系统环境变量做最终覆盖。
    """
    loaded: dict[str, str] = {}
    for dotenv_path in dotenv_paths:
        if not dotenv_path.exists() or not dotenv_path.is_file():
            continue
        for key, value in dotenv_values(dotenv_path).items():
            if key and value is not None:
                loaded[key] = value
    return loaded


def _read_value(name: str, merged_values: dict[str, str], default: str = "") -> str:
    return merged_values.get(name, default)


def _read_bool(name: str, default: bool, merged_values: dict[str, str]) -> bool:
    value = _read_value(name, merged_values, "")
    if value == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _read_model(
    prefix: str,
    default_provider: str,
    default_model: str,
    merged_values: dict[str, str],
    provider_defaults: dict[str, dict[str, str]] | None = None,
) -> ModelConfig:
    provider = _read_value(f"ITOPS_{prefix}_PROVIDER", merged_values, default_provider)
    defaults = (provider_defaults or {}).get(provider.lower(), {})

    # 用户显式配置优先；未配置时使用供应商默认值；都没有则用传入的 default
    endpoint = _read_value(f"ITOPS_{prefix}_ENDPOINT", merged_values, "") or defaults.get("endpoint", "")
    model = _read_value(f"ITOPS_{prefix}_MODEL", merged_values, "") or defaults.get("model", default_model)
    api_key = _read_value(f"ITOPS_{prefix}_API_KEY", merged_values, "") or defaults.get("api_key", "")

    return ModelConfig(
        provider=provider,
        model=model,
        endpoint=endpoint,
        api_key=api_key,
    )


def load_runtime_settings(
    env: dict[str, str] | None = None,
    dotenv_paths: tuple[Path, ...] = _DEFAULT_DOTENV_PATHS,
) -> RuntimeSettings:
    """
    Python 侧优先读取 .env 文件，再由调用方显式传入的环境变量覆盖。
    这样既支持本地文件配置，也保留部署环境的注入能力。
    """
    dotenv_values_map = _load_dotenv_values(dotenv_paths)
    merged_values = {**dotenv_values_map, **dict(os.environ if env is None else env)}
    milvus_uri = _read_value("ITOPS_MILVUS_URI", merged_values, "http://localhost:19530")
    return RuntimeSettings(
        milvus_uri=milvus_uri,
        milvus_collection_name=_read_value("ITOPS_MILVUS_COLLECTION", merged_values, "sop_catalog"),
        embedding=_read_model("EMBEDDING", "ollama", "bge-m3:latest", merged_values, EMBEDDING_PROVIDER_DEFAULTS),
        chat=_read_model("CHAT", "mock", "mock-chat", merged_values, CHAT_PROVIDER_DEFAULTS),
    )


def get_runtime_settings() -> RuntimeSettings:
    return load_runtime_settings()
