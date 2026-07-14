from __future__ import annotations

from agent_runtime.config import load_runtime_settings


def test_should_read_values_from_env():
    settings = load_runtime_settings(
        env={
            "ITOPS_MILVUS_URI": "http://127.0.0.1:19530",
            "ITOPS_MILVUS_COLLECTION": "ops_sop",
            "ITOPS_EMBEDDING_PROVIDER": "openai",
            "ITOPS_EMBEDDING_MODEL": "text-embedding-3-small",
            "ITOPS_CHAT_PROVIDER": "openai",
            "ITOPS_CHAT_MODEL": "gpt-4.1-mini",
        },
        dotenv_paths=(),
    )

    assert settings.milvus_uri == "http://127.0.0.1:19530"
    assert settings.milvus_collection_name == "ops_sop"
    assert settings.embedding.provider == "openai"
    assert settings.embedding.model == "text-embedding-3-small"
    assert settings.chat.provider == "openai"
    assert settings.chat.model == "gpt-4.1-mini"


def test_environment_variable_should_override_dotenv():
    settings = load_runtime_settings(
        env={
            "ITOPS_CHAT_PROVIDER": "openai",
            "ITOPS_CHAT_MODEL": "gpt-4.1",
        },
        dotenv_paths=(),
    )

    assert settings.chat.provider == "openai"
    assert settings.chat.model == "gpt-4.1"


def test_milvus_should_have_sensible_defaults():
    settings = load_runtime_settings(env={}, dotenv_paths=())
    assert settings.milvus_uri == "http://localhost:19530"
    assert settings.milvus_collection_name == "sop_catalog"
