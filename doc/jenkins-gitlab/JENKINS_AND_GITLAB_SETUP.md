# Setup for Programming Exercises with Jenkins and GitLab

This page describes how to set up a programming exercise environment based on Jenkins and GitLab. 
Optional commands, partially optional commands are in curly brackets <code>{}</code>.

<b>The following assumes that all instances run on separate servers. 
If you have one single server, or your own NGINX instance, just skip all NGINX related steps and us the configurations provided under _Separate NGINX Configurations_</b>

1. [GitLab](#gitlab)
2. [Jenkins](#jenkins)

## GitLab
1. Pull the latest GitLab Docker image

        docker pull gitlab/gitlab:ce-latest
        
2. Run the image

        docker run -itd --name gitlab
            --hostname your.gitlab.domain.com \
            --restart always \
            -p 80:80 -p 443:443 {-p 22:22} \     # If you are NOT running your own NGINX instance
            -p <some port of your choosing>:80    # If you ARE running your own NGINX instance
            -v gitlab_data:/var/opt/gitlab \
            -v gitlab_logs:/var/log/gitlab \
            -v gitlab_config:/etc/gitlab gitlab/gitlab-ce:latest
            
3. Wait a couple of minutes until GitLab is set up, then open the instance in you browser and set a first admin password of your choosing. 
You can then login using the username "root" and you password.

4. We recommend to rename the "root" admin user to "artemis". 
Use the same password in the Artemis configuration file _application-prod.yml_

        artemis:
            version-control:
                user: artemis
                password: the.password.you.chose
            
5. **If you run your own NGINX, the skip the next steps (6-7)**

6. Create the SSL directory in the GitLab Docker image, where you will store the certificate and key of your server and copy the certificate (fullchain) and key
mkdir -p /etc/gitlab/ssl

        docker exec -it gitlab /bin/bash
        exit
        docker cp path.to.your.fullchain.cert gitlab:/etc/gitlab/ssl/your.gitlab.domain.crt
        docker cp path.to.your.key gitlab:/etc/gitlab/ssl/your.gitlab.domain.key
        
7. Update the GitLab config file, so that you force https and redirect all http traffic to https

        docker exec -it gitlab /bin/bash
        vim /etc/gitlab/gitlab.rb
        # Now, search for the 'external_url' key (should be in the first 30 lines) and use the following values here:
        external_url 'https://your.gitlab.domain.com'
        nginx['redirect_http_to_https'] = true
        # Save your changes and finally run
        gitlab-ctl reconfigure

## Jenkins

### Jenkins Server Setup
1. Pull the latest Jenkins LTS image

        docker pull jenkins/jenkins:lts
        
2. Create a folder on your host machine containing your fullchain certificate and key

3. Run Jenkins

        docker run -itd --name jenkins \
            --restart always \
            -v jenkins_data:/var/jenkins_home \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -e VIRTUAL_HOST=your.jenkins.domain -e VIRTUAL_PORT=8080 \ # If you are NOT using a separate NGINX instance
            -p 8080:8080                                               # If you ARE using a separate NGINX instance
            jenkins/jenkins:lts
            
4. Run the NGINX proxy docker container, this will automatically setup all reverse proxies and force https on all connections. 
(This image would also setup proxies for all other running containers that have the VIRTUAL_HOST and VIRTUAL_PORT environment variables). 
**Skip this step if you have your own NGINX instance.**

        docker run -itd --name nginx_proxy \
            -p 80:80 -p 443:443 \
            --restart always \
            -v /var/run/docker.sock:/tmp/docker.sock:ro \
            -v path.to.your.cert:/etc/nginx/certs jwilder/nginx-proxy
            
5. Open Jenkins in your browser and setup the admin user account (install all suggested plugins). 
You can get the initial admin password using the following command.

        # Jenkins highlights the password in the logs, you can't miss it
        docker logs -f jenkins

6. Set the chosen credentials in the Artemis configuration _application-prod.yml_

        artemis:
            continuous-integration:
                user: your.chosen.username
                password: your.chosen.password
                
### Required Jenkins Plugins
You will need to install the following plugins (apart from the recommended ones that got installed during the setup process):
* [GitLab](https://plugins.jenkins.io/gitlab-plugin/) for enabling webhooks to and from GitLab
* [Multiple SCMs](https://plugins.jenkins.io/multiple-scms/) for combining the exercise test and assignment repositories in one build
* [Post Build Task](https://plugins.jenkins.io/postbuild-task/) for preparing build results to be exported to Artemis
* [Xvfb](https://plugins.jenkins.io/xvfb/) for exercises based on GUI libraries, for which tests have to have some virtual display
* [Timestamper](https://plugins.jenkins.io/timestamper/) for adding the time to every line of the build output

### Timestamper Configuration
Go to _Manage Jenkins → Configure System_. 
There you will find the Timestamper configuration, use the following value for both formats:

        '<b>'yyyy-MM-dd'T'HH:mm:ssZ'</b> '

![](timestamper_config.png)

### Jenkins Credentials
Go to _Credentials → Jenkins → Global credentials_ and create the following credentials

#### GitLab API Token
1. Login to GitLab using the Artemis admin account and go to the profile settings (upper right corned → _Settings_)

   <details><summary>Screenshot</summary>
   ![](gitlab_setting_button.png)
   </details>

2. Go to _Access Tokens_
