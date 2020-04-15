# Setup for Programming Exercises with Bamboo, Bitbucket and Jira

This page describes how to set up a programming exercise environment based on Bamboo, Bitbucket and Jira. 


**Prerequisites:** 
* [Docker](https://docs.docker.com/install)
* [Docker-Compose](https://docs.docker.com/compose/install/)

# Content of this document

1. [Start Docker-Compose](#docker-compose)
2. [Configure Bamboo, Bitbucket and Jira](#configure-bamboo-bitbucket-and-jira)
3. [Configure Artemis](#configure-artemis)


## Docker-Compose

Before you start the docker-compose, check if the bamboo version in the `build.gradle` is equal to the bamboo version number in the Dockerfile of bamboo stored in `docker/bamboo/Dockerfile`.
If the version number is not equal adjust the version number 

Execute the docker-compose file `atlassian.yml` stored in `main/docker`

<b>Get evaluation licenses for Atlassian products:</b> [Atlassian Licenses](https://my.atlassian.com/license/evaluation)

## Configure Bamboo, Bitbucket and Jira

1. Create an admin user with the same credentials in all 3 applications
2. Execute the shell script `atlassian-setup.sh` in the `main/docker` directory. This script creats groups, users and add them to
the created groups and diabled application links between the 3 applications   
3. Enable the created [application links](https://confluence.atlassian.com/doc/linking-to-another-application-360677690.html) between all 3 application (OAuth Impersonate)
4. Use the [user directories in Jira](https://confluence.atlassian.com/adminjiraserver/allowing-connections-to-jira-for-user-management-938847045.html) to synchronize the users in bitbucket and bamboo
5. In Bamboo create a global variable named <b>SERVER_PLUGIN_SECRET_PASSWORD</b>, the value of this variable will be used as the secret. The value of this variable
should be then stored in the `application-artemis.yml` as the value of `artemis-authentication-token-value`
6. Download the [bamboo-server-notifaction-plugin](https://github.com/ls1intum/bamboo-server-notification-plugin/releases) and add it to bamboo.
Go to Bamboo → Manage apps → Upload app → select the downloaded .jar file → Upload
  
## Configure Artemis

1. Modify the application-artemis.yml


    artemis:
        repo-clone-path: ./repos/
        repo-download-clone-path: ./repos-download/
        encryption-password: artemis-encrypt     # arbitrary password for encrypting database values
        user-management:
            use-external: true
            external:
                url: http://localhost:8081
                user:  <jira-admin-user>
                password: <jira-admin-password>
                admin-group-name: <group name which is used in all three applications(Bitbucket,Bamboo,Jira)>
            internal-admin:
                username: artemis_admin
                password: artemis_admin            
        version-control:
            url: http://localhost:7990
            user:  <bitbucket-admin-user>
            password: <bitbuckt-admin-password>
        continuous-integration:
            url: http://localhost:8085
            user:  <bamboo-admin-user>
            password: <bamboo-admin-password>
            vcs-application-link-name: <The application link name of bitbucket created in bamoo>
            empty-commit-necessary: true
            artemis-authentication-token-value: <artemis-authentication-token-value>

In order to find the `vcs-application-link-name`:
Go to Bamboo → Overview → Application links → The name of the bitbucket application is the `vcs-application-link-name` 

2. Modify the application-dev.yml


    server:
    port: 8080                                         # The port of artemis
    url: http://172.20.0.1:8080                        # needs to be an ip
    
In addition, you have to start Artemis with the profiles `bamboo`, `bitbucket` and `jira` so that the correct adapters will be used, e.g.:

    --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis

Please read [Development Setup](doc/setup/SETUP.md) for more details.
