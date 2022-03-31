# Local GitLab and GitLabCI setup

Run the following command to start GitLab and a GitLab Runner in a Docker container:
```bash
docker-compose -f src/main/docker/gitlab-gitlabci.yml up --build -d
```

Then navigate to http://localhost:8081/ login and then go to http://localhost:8081/admin/runners.
Click on Register an instance runner and copy the registration token and execute the following command with your token:
````bash
docker run --rm -v artemis-gitlabci-runner-config:/etc/gitlab-runner gitlab/gitlab-runner register \
  --non-interactive \
  --executor "docker" \
  --docker-image alpine:latest \
  --url http://gateway.docker.internal:8081 \
  --clone-url http://gateway.docker.internal:8081 \
  --registration-token "PROJECT_REGISTRATION_TOKEN" \
  --description "docker-runner" \
  --maintenance-note "Just a random local test runner" \
  --tag-list "docker,artemis" \
  --run-untagged="true" \
  --locked="false" \
  --access-level="not_protected"

````
If you experience problems while installing or registering the runner, you can have a look at the documentation for docker (https://docs.gitlab.com/runner/install/docker.html, https://docs.gitlab.com/runner/register/index.html#docker)
