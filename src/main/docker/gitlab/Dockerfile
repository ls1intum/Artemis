FROM gitlab/gitlab-ce:latest

LABEL description="Gitlab for local development environment packaged with jq"

# Install jq for retrieving json values.
RUN apt update
RUN apt-get install --no-install-recommends -y jq

# Prevent force password reset on next login when password is changed through through API
RUN sed -i '/^.*user_params\[:password_expires_at\] = Time.current if admin_making_changes_for_another_user.*$/s/^/#/' /opt/gitlab/embedded/service/gitlab-rails/lib/api/users.rb
