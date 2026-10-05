# Tiny container for the mock OpenAI-compatible LLM used by the Iris e2e stack.
# Pure Python stdlib, so a slim base with no pip install is enough.
FROM python:3.13-slim@sha256:3dd7cc108ec1493442514f5c2a871af6af0ec31d768ff6e378a93340c3b3db5f

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
