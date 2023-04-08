FROM artemis:coverage-latest as runtime

USER root

RUN echo "Installing needed dependencies" \
  && apt-get update && apt-get install -y --no-install-recommends unzip \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Install Jacocco Agent
RUN wget "https://search.maven.org/remotecontent?filepath=org/jacoco/jacoco/0.8.8/jacoco-0.8.8.zip" -O temp.zip \
  && unzip temp.zip "lib/jacocoagent.jar" -d . \
  && mv lib/jacocoagent.jar . \
  && rm -rf lib temp.zip
