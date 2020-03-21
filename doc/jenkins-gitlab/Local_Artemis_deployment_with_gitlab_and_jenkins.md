#Deployment Artemis / GitLab / Jenkins on Local machine

Tested on Development Branch - 20.03.2020

##Requirements
1. Docker
1. Docker-Compose
1. Cloned Artemis repo

##Preparation Steps
1. Create a Docker network named "artemis" with `docker network create artemis`


##Gitlab

1. Run 
 `docker pull gitlab/gitlab-ce`
 
 1. Run
 `docker run -itd --name gitlab --hostname localhost --restart always -p 80:80 -v gitlab_data:/var/opt/gitlab -v gitlab_logs:/var/log/gitlab -v gitlab_config:/etc/gitlab gitlab/gitlab-ce:latest`
 
1. Wait until container deployed. 

1. Add the Gitlab container to the created network with `docker network connect artemis gitlab`

1. Open the browser at `localhost:80` and set a first admin password. 

1. Login with `root` and setted password.

1.  Goto "Settings" - "Account" and rename `root` to `artemis`

1. Set the same password and the username in `src/main/resources/config/application-artemis.yml` at `artemis: version-control: user: <HERE> and passowrd <HERE>`and change url at `artemis: version-control: url: <HERE>` with the first ip returned by`docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' gitlab`

1. Login to GitLab using the new Artemis admin account and go to "Settings" - "Access Tokens" and create a new token named "Artemis", no expire date and give it all rights.

1. Copy the generated token and insert it into the Artemis configuration file `src/main/resources/config/application-artemis.yml` at `artemis: version-control: secret: <Here>`

1. For Localhost deployment go to: "admin area" - "settings" - "network" - "outbound requests" and check both boxes allowing local netowrking

 
##Jenkins

1. Run
`docker pull jenkins/jenkins:lts`

1. Run
`docker run -itd --name jenkins --restart always -v jenkins_data:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock -p 8080:8080 jenkins/jenkins:lts`
 
1. Wait until container is deployed. 

1. Add the Jenkins container to the created network with `docker network connect artemis jenkins`

1. Open the browser at `localhost:8080`

1. To retrieve the needed password run:
`docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`

1. Install suggested Plugins

1. Create first Admin User and add USERNAME and PASSWORD to `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: user: <USERNAME> and password: <PASSWORD>` and change url at `artemis: continuous-integration: url <HERE>` with the first ip returned by `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' jenkins`

1. Install the other required plugins by going to "Manage Jenkins" - "Manage Plugins" - "Available", selecting:  `GitLab, Multiple SCMs, Post Build Task, Xvfb, (Timestamper - already installed in case it can't be found)`, clicking "Download now and install after restart" and checking the "Restart Jenkins when installation is complete and no jobs are running" box

1. Reload page and log in using the previous set credentials

####Configure Timestamper

1. Go to "Manage Jenkins" - "Configure System"

1. Change the two Timestamper formats with `'<b>'yyyy-MM-dd'T'HH:mm:ssZ'</b> '` and Apply! / Save!

####Configure Server Notification Plugin

1. Download the ".hpi" plugin at: https://github.com/ls1intum/jenkins-server-notification-plugin/releases/tag/v1.0.0

1. Go to "Manage Jenkins" - "Manage Plugins" - "Advanced" and install the downloaded Plugin with "Upload Plugin", install and restart

####Jenkins Credentials

1. Go to `localhost:80` (local gitLab instance), login and go to "Settings" - "Access Tokens" and create a new token named `Jenkins` with `api` and `read_repository` rights

1. Copy the generated token

1. Go back to `localhost:8080` (local Jenkins instance) "Credentials" - "Jenkins" - "Global" and add new credentials with: 
	1. Kind: GitLab API token
	2. API token: the_previous_copied_token
	3. Leave ID field blank
	4. Description is up to you

1. Get the ip of the container running the gitlab instance by running `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' gitlab` and copy the first ip
	
1. Go to: "Manage Jenkins" - "Configure System" and fill the Gitlab fields with:
	1. Connection name: is up to you
	2. Gitlab host URL:  the_copied_container_ip
	3. Credentials: the previous created GitLab API token

1. Test Connection and Save

1. Go to "Credentials" - "Global credentials" - "GitLab API token" - "Update" copy the ID field and paste it in `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: vcs-credentials: <API_ID>`

#### Server Notification Token:

1.     Create a new Jenkins credential containing a token, which gets send by the server notification plugin to Artemis with every build result:
	1. Kind: Secret text
	1. Secret: your.secret_token_value (choose any value you want, copy it for the nex step) 
	1. Leave the ID field blank 
	1. The description is up to you
	
1. Paste the copied secret to `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: artemis-authentication-token-value: <HERE>`

1. Copy the generated ID of the new credentials and put it into  `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: artemis-authentication-token-key: <HERE>`


#### Gitlab Repository Access
1. Create ane jenkins credentials with username and password of GitLab admin account: go to: "Credentials" - "Global credentials" - "Add Credentials"

1. Add a new credential with:
	1. Kind: Username and password
	2. Scope: global
	3. Username: <Gitlab_admin_username>
	4. Password: <Gitlab_admin_password>
	5. ID: blank
	6. Description: Up to you
	
2. Copy the credential ID and paste it at `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: vcs-credentials: <API_ID>`

#### Gitlab to Jenkins push notification token

1. Create a new item in Jenkins (Freestyle project type) and name it `test`

2. Check "Build when a change is pushed to GitLab. GitLab webhook URL" and go to "Advanced.."

3. Click generate for a new secret token, copy it and click "Apply"

3. Paste the secret token in `src/main/resources/config/application-artemis.yml` at `artemis: version-control: ci-token: <token>`

4. Perform a GET request to the following URL (e.g. with Postman) using Basic Authentication and the username and password you chose for the Jenkins admin account:
` GET https://your.jenkins.domain/job/test/config.xml`

5. You will get the whole configuration XML of the just created build plan, there you will find the following tag:
`<secretToken>{$some-long-encrypted-value}</secretToken>`

6. Copy the value of $some-long-encrypted-value without the curly brackets! and delete the project

7. Paste the encrypted value to: `src/main/resources/config/application-artemis.yml` at `artemis: continuous-integration: secret-push-token: <encrypted_value>`

8. (For localhost) disable CSRF by going to: "Manage Jenkins" - "Configure Global Security" and uncheck "Prevent Cross Site Request Forgery exploits"

#### GitLab Health Token

1. Go to: "GitLab" - "Admin Area" - "Monitoring" - "Health Check" and copy the "Access token"

2. Paste the token in `src/main/resources/config/application-artemis.yml` at `artemis: version-control: health-api-token: <token>`

#### Jenkins + Maven + Java 12

In order to install and use Maven with Java 12 in the Jenkins container, you have to first install maven, then download Java 12 and findall configure Maven to use Java 12 instead of the default version:
```
docker exec -it -u root jenkins /bin/bash
apt update
apt install maven
cd /usr/lib/jvm
wget https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz
tar -zxf OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz && mv jdk-12.0.2+10 java-12-openjdk-amd64
chown -R root:root java-12-openjdk-amd64
```

While still having the shell in the Jenkins container open, install your preferred editor (e.g. vim using `apt install vim`) and open /usr/share/maven/bin/mvn and paste the following into the first line (after the header comments):
```
JAVA_HOME="/usr/lib/jvm/java-12-openjdk-amd64"
```

Save and close the file. Run the command `mvn --version` to verify that Maven with Java 12 works as expected. You can now delete `OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz`.

## Artemis

1. In `checkstyle/checkstyle.xml` change both "Severity" from `error` to `warning` to avoid docker stop the deployment

1. In `java/de/tum/in/www1/artemis/service/connectors/jenkins/JenkinsAuthorizationInterceptor.java` remove or comment out the line containing `setCrumb(request.getHeaders());` (line 38 currently)

1. In `java/de/tum/in/www1/artemis/service/connectors/jenkins/JenkinsService.java` change in the line containing `jenkinsServer.createFolder(programmingExercise.getProjectKey(), true);` (currently line 480) the "true" parameter with "false"

3. In `src/main/resources/config/application-artemis.yml` 
	1. Choose a password for `artemis: encryption-password:` (note that in this example "very-secret" is used as encryption password. If you want to use another one, the encrypted password of the following SQL query has to be changed with a new one that can be generated on https://www.devglan.com/online-tools/jasypt-online-encryption-decryption using the chosen encryption-password) 
	2. Set `artemis: user-management: use_external` to false

4. In `docker-compose.yml` 
	1. Change the `8080:8080` port to `8081:8081` as we already use the 8080 port for Jenkins
	1. Change the node version with a newer one ( > 12.8.0) at `services: artemis-client: image:`
	1. Change the SPRING_PROFILES_ACTIVE to ¸dev,jenkins,gitlab,artemis

5. In `src/main/resources/config/application-dev.yml`

	1. At `spring: profiles: active:` add `gitlab & jenkins`

	2. At `spring: liquibase:` add the new property `change-log: classpath:config/liquibase/master.xml`
	
	3. At `server:` change port to 8081 and 


6. Run `docker-compose up`

6. After the container has been deployed run `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' artemis_artemis-server_1` and copy the first resulting ip.

5. In `src/main/resources/config/application-dev.yml` at `server:` change the port to 8081 and at `url:` paste the copied ip

6. Stop the containers with STRG - C and re-run `docker-compose up`

7. Execute the following SQL in your local Database ``INSERT INTO `artemis`.`jhi_user` (`id`, `login`, `password_hash`, `first_name`, `last_name`, `email`, `activated`, `lang_key`, `activation_key`, `created_by`, `created_date`, `last_modified_by`, `last_modified_date`, `last_notification_read`) VALUES ('1', 'admin1', 'tnuRaoUvEZxV5JLuWJ4nmjUdg+YCTK6R', 'Ad', 'min', 'Admin@uibk.ac.at', 1, 'en', 'g90KRFEvWvGFGZhXJwLd', 'anonymousUser', '2020-03-13 00:04:43', 'anonymousUser', '2020-03-13 09:39:52', '2020-03-13 15:37:37');`` and ``INSERT INTO `artemis`.`jhi_user_authority` (`user_id`, `authority_name`) VALUES ('1', 'ROLE_ADMIN');`` and ``INSERT INTO `artemis`.`jhi_user_authority` (`user_id`, `authority_name`) VALUES ('1', 'ROLE_USER');`` to add the first admin user. `(Username: admin1, Password: password)`


#### Workaround for testing purpose to URL related problems to Jenkins and Gitlab

1. In case Servies as Jenkins or Gitlab can't be reached by Artemis, change the URL's in `src/main/resources/config/application-artemis.yml` with ones generated with "Ngrok" by running `ngrok http <port>`

Further info at `https://ngrok.com/`

	