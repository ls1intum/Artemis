FROM gitlab/gitlab-ce:latest

LABEL description="Gitlab for local development environment packaged with jq"
# Install jq for retrieving json values.
RUN apt update
RUN apt-get install -y jq
