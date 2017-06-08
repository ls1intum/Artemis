# ArTEMiS Application

These are development instructions for the ArTEMiS application.

## Development Docker Container Setup
Docker can be used to setup development containers for MySQL, Bitbucket, Bamboo and JIRA.

1. Install Docker and `docker-compose`
2. Run `docker-compose -f src/main/docker/dev.yml up`. 
3. This will startup the following containers. When accessing for the first time you need to setup a license and an admin user. 
    1. Bitbucket: [http://127.0.0.1:7990](http://127.0.0.1:7990)
    2. Bamboo: [http://127.0.0.1:8085](http://127.0.0.1:8085)
    3. JIRA: [http://127.0.0.1:8000](http://127.0.0.1:8000)
    4. MySQL: 127.0.0.1:3306 (user `root` without password)
4. In Bamboo go to `Administration` -> `Application Links` and add Bitbucket using the URL `http://exerciseapplication-bitbucket:7990`. Use OAuth without Impersonation.
5. In Bitbucket go to `Administration` -> `Application Links` and add Bamboo using the URL `http://exerciseapplication-bamboo:8085`. Use OAuth without Impersonation.
6. Configure JIRA/Bitbucket/Bamboo with Groups, Test Users, Plans etc. Please note that as default JIRA/Bitbucket/Bamboo do have seperate user databases and test users might need to be created on all instances.
7. Setup your `application.yml` in the root directory of the app, example:
    
        exerciseapp:
          repo-clone-path: ./repos/
          result-retrieval-delay: 5000
          encryption-password: X7RNnJUzeoUpB2EQsK
          jira:
            url: http://localhost:8000
            instructor-group-name: jira-administrators
          bitbucket:
            url: http://localhost:7990
            user: bitbucket
            password: bitbucket
          bamboo:
            url: http://localhost:8085
            bitbucket-application-link-id: 0c3af16d-2aef-3660-8dd8-4f87042833de
            user: bamboo
            password: bamboo
          lti:
            oauth-key: exerciseapp_lti_key
            oauth-secret: 7pipQv9MeidmZvMsTL
            create-user-prefix: edx_

    
## Profiles

ArTEMiS uses Spring profiles to segregate parts of the application configuration and make it only available in certain environments. For development purposes, the following program arguments can be used to enable the `dev` profile and the profiles for JIRA, Bitbucket and Bamboo:


    --spring.profiles.active=dev,bamboo,bitbucket,jira 


