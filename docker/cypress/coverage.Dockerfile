FROM artemis:coverage-latest

RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends unzip \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

COPY docker/cypress/bootstrap-coverage.sh /bootstrap.sh

RUN chmod +x /bootstrap.sh
