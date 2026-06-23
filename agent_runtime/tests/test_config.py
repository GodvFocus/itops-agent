from __future__ import annotations

from pathlib import Path

from agent_runtime.config import load_runtime_settings


def test_should_read_values_from_dotenv_file(tmp_path: Path) -> None:
    dotenv = tmp_path / ".env"
    dotenv.write_text(
        "\n".join([
            "ITOPS_QDRANT_ENABLED=true",
            "ITOPS_QDRANT_URL=http://127.0.0.1:6333",
            "ITOPS_QDRANT_COLLECTION=ops_sop",
            "ITOPS_EMBEDDING_PROVIDER=openai",
            "ITOPS_EMBEDDING_MODEL=text-embedding-3-small",
            "ITOPS_CHAT_PROVIDER=openai",
            "ITOPS_CHAT_MODEL=gpt-4.1-mini",
        ]),
        encoding="utf-8",
    )

    settings = load_runtime_settings(env={}, dotenv_paths=(dotenv,))

    assert settings.qdrant_enabled is True
    assert settings.qdrant_url == "http://127.0.0.1:6333"
    assert settings.qdrant_collection_name == "ops_sop"
    assert settings.embedding.provider == "openai"
    assert settings.embedding.model == "text-embedding-3-small"
    assert settings.chat.provider == "openai"
    assert settings.chat.model == "gpt-4.1-mini"


def test_environment_variable_should_override_dotenv(tmp_path: Path) -> None:
    dotenv = tmp_path / ".env"
    dotenv.write_text(
        "\n".join([
            "ITOPS_CHAT_PROVIDER=mock",
            "ITOPS_CHAT_MODEL=mock-chat",
        ]),
        encoding="utf-8",
    )

    settings = load_runtime_settings(
        env={
            "ITOPS_CHAT_PROVIDER": "openai",
            "ITOPS_CHAT_MODEL": "gpt-4.1",
        },
        dotenv_paths=(dotenv,),
    )

    assert settings.chat.provider == "openai"
    assert settings.chat.model == "gpt-4.1"
