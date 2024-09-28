#!/bin/bash

# Enter the personal access token of the admin user
ADMIN_PERSONAL_ACCESS_TOKEN="artemis-gitlab-token"

# Enter the url of the Gitlab instance.
GITLAB_API_URL="http://localhost/api/v4/"

# Whether new access tokens should be generated
GENERATE_ACCESS_TOKENS="false"

# Allow outbound requests to local network
echo 'Allowing outbound requests to local network...'
outbound_requests_allowed=$(curl -s --request PUT --header "Authorization: Bearer $ADMIN_PERSONAL_ACCESS_TOKEN" "$GITLAB_API_URL/application/settings?allow_local_requests_from_hooks_and_services=true&allow_local_requests_from_web_hooks_and_services=true&allow_local_requests_from_system_hooks=true" | jq -r .allow_local_requests_from_web_hooks_and_services)
if $outbound_requests_allowed
then
    echo "Success."
else
    echo "Failed to allow outbound requests to local network. Go to <https://gitlab-url>/admin/application_settings/network → Outbound requests and enable it."
fi

if [[ "$GENERATE_ACCESS_TOKENS" == "false" ]]
then
  echo "Will not create new access tokens."
  exit
fi

# Generate access token for Artemis
echo 'Generating personal access token for Artemis with api, read_user, read_api, read_repository, write_repository, and sudo scopes.'
artemis_access_token=$(curl -s --request POST --header "Authorization: Bearer $ADMIN_PERSONAL_ACCESS_TOKEN" --data "name=Artemis" --data "scopes[]=api,read_user,read_api,read_repository,write_repository,sudo" "$GITLAB_API_URL/users/1/personal_access_tokens" | jq -r .token)
echo "Success."

# Generate access token for Jenkins
echo 'Generating personal access token for Jenkins with api and read_repository scopes.'
jenkins_access_token=$(curl -s --request POST --header "Authorization: Bearer $ADMIN_PERSONAL_ACCESS_TOKEN" --data "name=Jenkins" --data "scopes[]=api,read_repository" "$GITLAB_API_URL/users/1/personal_access_tokens" | jq -r .token)
echo "Success."

echo
echo
echo 'The Artemis access token has been created and can be copied into your application-local.yml file:'
echo
echo "
artemis:
    version-control:
        token: $artemis_access_token
...
"

echo
echo 'The access Jenkins token has been created and can be copied into your jenkins-casc-config-gitlab.yml file:'
echo "
credentials:
  system:
    domainCredentials:
      - credentials:
          - gitLabApiTokenImpl:
              id: artemis_gitlab_api_token
              scope: GLOBAL
              description: 'Gitlab Api Token for Artemis'
              apiToken: $jenkins_access_token
"
