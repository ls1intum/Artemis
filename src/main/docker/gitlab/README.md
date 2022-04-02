# GitLab and GitLab CI setup

First, configure the environment parameters:
```bash
cp src/main/docker/env.example.gitlab-gitlabci.txt src/main/docker/.env
vi src/main/docker/.env
```

Run the following command to start GitLab and a GitLab Runner in a Docker container:
```bash
docker-compose -f src/main/docker/gitlab-gitlabci.yml --env-file src/main/docker/.env up --build -d
```

Then log on to http://localhost/ with the password (`sudo docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password`) and go to http://localhost/admin/runners.
Click on "Register an instance runner" and copy the registration token.
Open a shell into the `gitlab-runner` container:
```bash
docker exec -it gitlab-runner /bin/bash
```
Execute the following command with your token inside the container with the registration token generated above (If you use Linux, try `--clone-url http://172.17.0.1:80 \`, because `gateway.docker.internal` is only for [Windows](https://docs.docker.com/desktop/windows/networking/#use-cases-and-workarounds)):
```bash
gitlab-runner register \
  --non-interactive \
  --executor "docker" \
  --docker-image alpine:latest \
  --url http://gitlab:80 \
  --clone-url http://gateway.docker.internal:80 \
  --registration-token "PROJECT_REGISTRATION_TOKEN" \
  --description "docker-runner" \
  --maintenance-note "Just a random local test runner" \
  --tag-list "docker,artemis" \
  --run-untagged="true" \
  --locked="false" \
  --access-level="not_protected"
```

If you experience problems while installing or registering the runner, you can have a look at the documentation for docker (https://docs.gitlab.com/runner/install/docker.html, https://docs.gitlab.com/runner/register/index.html#docker)
