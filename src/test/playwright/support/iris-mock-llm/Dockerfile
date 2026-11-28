# Tiny container for the mock OpenAI-compatible LLM used by the Iris e2e stack.
# Pure Python stdlib, so a slim base with no pip install is enough.
FROM python:3.14-slim@sha256:f57c4c23b985d95deea5bc5249e2f9c8a512b4cc9d5d3fd31900cbd2f5f0a604

# Run as a non-root user (low-cost hardening; the mock binds an unprivileged port).
RUN useradd --create-home --shell /usr/sbin/nologin mockllm

WORKDIR /app
COPY mock_llm.py /app/mock_llm.py
RUN chown -R mockllm:mockllm /app

ENV MOCK_LLM_HOST=0.0.0.0
ENV MOCK_LLM_PORT=8081
EXPOSE 8081

USER mockllm
CMD ["python", "/app/mock_llm.py"]
