#!/bin/bash
###################################################################################################
# Gitlab setup script                                                                             #
# If you have any questions, ask Simon Leiß <simon.leiss@tum.de>                                  #
# This script can be run by using the command                                                     #
# docker compose -f src/main/docker/gitlab-jenkins.yml exec artemis-gitlab /./gitlab-setup.sh     #
###################################################################################################


echo "======================================================================================="
echo "Welcome to the Gitlab setup script".
echo "This script will guide you through the setup of the Gitlab usage with Artemis."
echo "If you make any mistakes, please cancel the execution and start from the beginning."
echo "======================================================================================="
echo


read -p "Do you want to use the nginx configuration bundled with Artemis? This will include the setup of LetsEncrypt certificates [y/N].`echo $'\n> '`" -n 1 -r
echo
NGINX=$REPLY
if [[ $NGINX =~ ^[Yy]$ ]]
then
    echo
    echo "Alright, we will collect the information needed nginx now."
    read -p "What email address should be used as contact for the certificate? [e.g. \"gitlab@your.gitlab.domain.com\"]. Do not include any quotation marks.`echo $'\n> '`" -r
    echo
    CERT_MAIL=$REPLY
    echo "Your email address is: $CERT_MAIL"
    echo

    read -p "Under what URL is this Gitlab instance reachable? Include the protocol. [e.g. \"https://your.gitlab.domain.com\"]. Do not include any quotation marks.`echo $'\n> '`" -r
    echo
    GITLAB_URL=$REPLY
    echo "Your Gitlab URL is: $GITLAB_URL"
    echo
fi
echo

read -p "From which ip addresses should the monitoring interface be reachable? Enter the ip address of your Artemis instance or enter \"0.0.0.0/0\" to allow all ip addresses. Do not include any quotation marks.`echo $'\n> '`" -r
echo
MONITORING_IP=$REPLY
echo "The monitoring interface will be reachable from: $MONITORING_IP"
echo

read -p "Do you want to change the default SSH port (22) [y/N]?.`echo $'\n> '`" -n 1 -r
echo
SSH_CHANGED=$REPLY
if [[ $SSH_CHANGED =~ ^[Yy]$ ]]
then
    echo
    echo "Alright, we will setup the new SSH port now. Make sure to use the same port in the docker compose file."
    read -p "What alternative SSH port should be used? [e.g. \"2222\"]. Do not include any quotation marks.`echo $'\n> '`" -r
    echo
    SSH_PORT=$REPLY
    echo "The new SSH port will be: $SSH_PORT"
    echo
fi


echo
echo "All data is now collected, but no changes have been applied."
echo
echo "==NOW IS THE LAST CHANCE TO ABORT=="
echo
read -p "Do you want to apply the changes? [y/N].`echo $'\n> '`" -n 1 -r
echo
APPLY=$REPLY
if [[ ! $APPLY =~ ^[Yy]$ ]]
then
    echo
    echo "ABORTED."
    echo "Restart this script if you want to start over again."
    echo "Have a good day!"
    echo
    exit 1
fi

echo
echo "Applying changes..."
echo

GITLAB_CONFIG_PATH="/etc/gitlab/gitlab.rb"

# Uncomment the following two lines when debugging
# GITLAB_CONFIG_PATH="gitlab_setup_test"
# touch $GITLAB_CONFIG_PATH

if [[ $NGINX =~ ^[Yy]$ ]]
then
    echo
    echo "Applying changes for nginx.."
    echo

    echo "letsencrypt['enable'] = true" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH
    echo "external_url \"$GITLAB_URL\"" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH
    echo "letsencrypt['contact_emails'] = ['$GITLAB_URL']" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH
    echo "nginx['redirect_http_to_https'] = true" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH
    echo "nginx['redirect_http_to_https_port'] = 80" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH

    echo "Applied changes.."
    echo

    echo "Reconfiguring Gitlab"
    echo
    gitlab-ctl reconfigure

    if [ $? -eq 0 ]
    then
        echo
        echo "Reconfiguring failed.. Trying to solve by renewing certificates."
        echo

        gitlab-ctl renew-le-certs
    fi

    echo "Certificates should now be generated & active."
    echo
fi

echo
echo "Updating monitoring whitelist"
echo

echo "gitlab_rails['monitoring_whitelist'] = ['$MONITORING_IP']" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH

if [[ $SSH_CHANGED =~ ^[Yy]$ ]]
then
    echo
    echo "Updating SSH port"
    echo
    echo "gitlab_rails['gitlab_shell_ssh_port'] = $SSH_PORT" | cat - $GITLAB_CONFIG_PATH > gitlab_setup_tmp && mv gitlab_setup_tmp $GITLAB_CONFIG_PATH
fi

echo
echo "Reconfiguring Gitlab"
echo

gitlab-ctl reconfigure

echo
echo "This script is now finished."
echo "If you have any problems, please restart the Gitlab container before doing anything else."
echo "If the problem persists, please ensure that the values in '$GITLAB_CONFIG_PATH' are set correctly by this script."
echo
echo "Have a nice day!"
