# Docker Configurations

We build the docker images in [this repository](https://github.com/ls1intum/Artemis-Local-Setup-Docker)

## Atlassian Setup

You can start a local Atlassian stack (Jira, Bitbucket, Bamboo) using the `atlassian.yml` docker-compose file.

Start vanilla atlassian stack: 
```
docker-compose -f atlassian.yml up -d 
```

Start atlassian stack which can execute `C` builds: 

```
docker-compose -f atlassian.yml -f atlassian.c.override.yml up -d 
```

Start atlassian stack which can execute `swift` builds: 
```
docker-compose -f atlassian.yml -f atlassian.swift.override.yml up -d 
```

## GitLab Setup

You can start a local GitLab stack (GitLab, Jenkins, MySQL) using the `gitlab-jenkins-mysql.yml` docker-compose file.

Start vanilla gitlab stack:
```
docker-compose -f gitlab-jenkins-mysql.yml up -d 
```

Start atlassian stack which can execute `swift` builds:
```
docker-compose -f atlassian.yml -f jenkins.swift.override.yml up -d 
```
