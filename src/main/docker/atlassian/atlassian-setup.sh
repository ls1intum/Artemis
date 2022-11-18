#!/bin/bash
# for configuring instance over script
jira_internal_port=8080
jira_external_port=8081
bamboo_port=8085
bitbucket_port=7990

same_credentials=false

set -e

while true; do
    read -p $'Are you using the same Username + Password on all three systems (Jira, Bamboo, Bitbucket)? \n' yn
    case $yn in
        [Yy]* ) same_credentials=true; break;;
        [Nn]* ) same_credentials=false; break;;
        * ) echo "Please answer yes or no.";;
    esac
done

if [ "$same_credentials" = true ] ; then
    echo In order to work, you need to provide the Username and Password of an admin account for Jira, Bamboo and Bitbucket
    echo Admin Account for ALL Systems
    read -p 'Username: ' all_uservar
    read -sp 'Password: ' all_passvar
    echo
    jira_uservar=$all_uservar
    jira_passvar=$all_passvar
    bamboo_uservar=$all_uservar
    bamboo_passvar=$all_passvar
    bitbucket_uservar=$all_uservar
    bitbucket_passvar=$all_passvar
else
    echo In order to work, you need to provide the Username and Password of an admin account for Jira, Bamboo and Bitbucket
    echo Jira Admin Account
    read -p 'Username: ' jira_uservar
    read -sp 'Password: ' jira_passvar
    echo

    echo Bamboo Admin Account
    read -p 'Username: ' bamboo_uservar
    read -sp 'Password: ' bamboo_passvar
    echo

    echo Bitbucket Admin Account
    read -p 'Username: ' bitbucket_uservar
    read -sp 'Password: ' bitbucket_passvar
    echo
fi

# create groups

declare -a group_names=("tutors" "instructors" "students" "editors")

jira_group_url="http://localhost:$jira_external_port/rest/api/latest/group"

for group_name in "${group_names[@]}"; do
    curl -u "$jira_uservar":"$jira_passvar" \
    -s \
    --header "Content-Type: application/json" \
    --request POST \
    --fail \
    --show-error \
    --data "{
                \"name\": \"$group_name\"
            }" \
    $jira_group_url
done

# create users

jira_user_url="http://localhost:$jira_external_port/rest/api/latest/user"
jira_group_add_url="http://localhost:$jira_external_port/rest/api/2/group/user?groupname="

for i in {1..20}; do
    # User 1-5 are students, 6-10 are tutors, 11-15 are editors and 16-20 are instructors
    group="students"
    if ((i > 5)); then
      group="tutors"
    fi
    if ((i > 10)); then
      group="editors"
    fi
    if ((i > 15)); then
      group="instructors"
    fi

    # Create user
    curl -u "$jira_uservar":"$jira_passvar" \
    -s \
    --header "Content-Type: application/json" \
    --request POST \
    --fail \
    --show-error \
    --data "{
                \"password\": \"artemis_test_user_$i\",
                \"emailAddress\": \"artemis_test_user_$i@artemis.local\",
                \"displayName\": \"Artemis Test User $i\",
                \"name\": \"artemis_test_user_$i\"

            }" \
    $jira_user_url

    # Add user to group
    curl -u "$jira_uservar":"$jira_passvar" \
    -s \
    --header "Content-Type: application/json" \
    --request POST \
    --fail \
    --show-error \
    --data "{
                \"name\": \"artemis_test_user_$i\"
            }" \
    "$jira_group_add_url$group"
done

# Application Links

jira_url="http://localhost:$jira_external_port/rest/applinks/latest/applicationlinkForm/createAppLink"
bamboo_url="http://localhost:$bamboo_port/rest/applinks/latest/applicationlinkForm/createAppLink"
bitbucket_url="http://localhost:$bitbucket_port/rest/applinks/latest/applicationlinkForm/createAppLink"

internal_jira_url="http://jira:$jira_internal_port"
internal_bamboo_url="http://bamboo:$bamboo_port"
internal_bitbucket_url="http://bitbucket:$bitbucket_port"

echo $'\nConfiguring ApplicationLinks'
# Jira
# add link from jira to bitbucket
curl -u "$jira_uservar":"$jira_passvar" \
    -s \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"stash\",
                    \"name\": \"LS1 Bitbucket Server\",
                    \"displayUrl\": \"$internal_bitbucket_url\",
                    \"rpcUrl\": \"$internal_bitbucket_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$bitbucket_uservar\",
                \"password\": \"$bitbucket_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_jira_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $jira_url
# add link from jira to bamboo
curl -u "$jira_uservar":"$jira_passvar" \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"bamboo\",
                    \"name\": \"LS1 Bamboo Server\",
                    \"displayUrl\": \"$internal_bamboo_url\",
                    \"rpcUrl\": \"$internal_bamboo_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$bamboo_uservar\",
                \"password\": \"$bamboo_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_jira_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $jira_url
# Bamboo
# add link from bamboo to bitbucket
curl -u "$bamboo_uservar":"$bamboo_passvar" \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"stash\",
                    \"name\": \"LS1 Bitbucket Server\",
                    \"displayUrl\": \"$internal_bitbucket_url\",
                    \"rpcUrl\": \"$internal_bitbucket_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$bitbucket_uservar\",
                \"password\": \"$bitbucket_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_bamboo_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $bamboo_url
# add link from bamboo to jira
curl -u "$bamboo_uservar":"$bamboo_passvar" \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"jira\",
                    \"name\": \"LS1 Jira Server\",
                    \"displayUrl\": \"$internal_jira_url\",
                    \"rpcUrl\": \"$internal_jira_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$jira_uservar\",
                \"password\": \"$jira_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_bamboo_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $bamboo_url
#Bitbucket
# add link from bitbucket to jira
curl -u "$bitbucket_uservar":"$bitbucket_passvar" \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"jira\",
                    \"name\": \"LS1 Jira Server\",
                    \"displayUrl\": \"$internal_jira_url\",
                    \"rpcUrl\": \"$internal_jira_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$jira_uservar\",
                \"password\": \"$jira_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_bitbucket_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $bitbucket_url
# add link from bitbucket to bamboo
curl -u "$bitbucket_uservar":"$bitbucket_passvar" \
    --fail \
    --show-error \
    --header "Content-Type: application/json" \
    --request POST \
    --data "{
                \"applicationLink\": {
                    \"typeId\": \"bamboo\",
                    \"name\": \"LS1 Bamboo Server\",
                    \"displayUrl\": \"$internal_bamboo_url\",
                    \"rpcUrl\": \"$internal_bamboo_url\",
                    \"isPrimary\": true,
                    \"isSystem\": true
                },
                \"username\": \"$bamboo_uservar\",
                \"password\": \"$bamboo_passvar\",
                \"customRpcURL\": false,
                \"rpcUrl\": \"$internal_bitbucket_url\",
                \"createTwoWayLink\": false,
                \"configFormValues\": {
                \"trustEachOther\": true,
                \"shareUserbase\": true
                }
            }" \
    $bitbucket_url
echo ApplicationLinks created
echo Please finish configuration of ApplicationLinks in browser

jira_application_links_url="http://localhost:$jira_external_port/plugins/servlet/applinks/listApplicationLinks"
bamboo_application_links_url="http://localhost:$bamboo_port/plugins/servlet/applinks/listApplicationLinks"
bitbucket_application_links_url="http://localhost:$bitbucket_port/plugins/servlet/applinks/listApplicationLinks"

if [ "$(uname)" == "Darwin" ]
then
    open $jira_application_links_url $bamboo_application_links_url $bitbucket_application_links_url
else
    xdg-open $jira_application_links_url
    xdg-open $bamboo_application_links_url
    xdg-open $bitbucket_application_links_url
fi
