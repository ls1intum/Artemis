ARG CYPRESS_BROWSER=node18.6.0-chrome105-ff104
FROM cypress/browsers:${CYPRESS_BROWSER}
RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends default-jre \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*
