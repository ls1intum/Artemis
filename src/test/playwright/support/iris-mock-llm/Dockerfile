# Tiny container for the mock OpenAI-compatible LLM used by the Iris e2e stack.
# Pure Python stdlib, so a slim base with no pip install is enough.
FROM python:3.13-slim

WORKDIR /app
COPY mock_llm.py /app/mock_llm.py

ENV MOCK_LLM_HOST=0.0.0.0
ENV MOCK_LLM_PORT=8081
EXPOSE 8081

CMD ["python", "/app/mock_llm.py"]
