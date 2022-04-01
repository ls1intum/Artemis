# Local GitLab and GitLab CI setup

First, configure the hostname and external URL in the `gitlab-gitlabci.yml` file and run the following command to start GitLab and a GitLab Runner in a Docker container:
```bash
docker-compose -f src/main/docker/gitlab-gitlabci.yml up --build -d
```

Then log on to https://localhost/ with the password (`sudo docker exec -it NAME_OF_THE_CONTAINER grep 'Password:' /etc/gitlab/initial_root_password`) and go to https://localhost/admin/runners.
Click on "Register an instance runner" and copy the registration token.
Open a shell into the `gitlab-runner` container:
`````bash
docker ps
docker exec -it gitlab-runner /bin/bash
`````
Execute the following command with your token inside the container with the registration token generated above:
````bash
gitlab-runner register \
  --non-interactive \
  --executor "docker" \
  --docker-image alpine:latest \
  --url https://EXTERNAL_URL \
  --registration-token "PROJECT_REGISTRATION_TOKEN" \
  --description "docker-runner" \
  --maintenance-note "Just a random local test runner" \
  --tag-list "docker,artemis" \
  --run-untagged="true" \
  --locked="false" \
  --access-level="not_protected"
````

For local use, the following command is probably required:
````bash
gitlab-runner register \
  --non-interactive \
  --executor "docker" \
  --docker-image alpine:latest \
  --url http://gateway.docker.internal:80 \
  --clone-url http://gateway.docker.internal:80 \
  --registration-token "PROJECT_REGISTRATION_TOKEN" \
  --description "docker-runner" \
  --maintenance-note "Just a random local test runner" \
  --tag-list "docker,artemis" \
  --run-untagged="true" \
  --locked="false" \
  --access-level="not_protected"
````

If you experience problems while installing or registering the runner, you can have a look at the documentation for docker (https://docs.gitlab.com/runner/install/docker.html, https://docs.gitlab.com/runner/register/index.html#docker)
