# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv *.war build/libs/

# Load git history needed for analysis
git config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"

git fetch --unshallow || git fetch --all

adminUser='${bamboo.artemis_admin_username}'
adminPassword='${bamboo.artemis_admin_password}'
artemisPort=54321

docker build . -f ./src/main/docker/Dockerfile -t artemis:coverage-latest
# Start Artemis docker containers with docker-compose
cd src/main/docker/cypress
# Export environmental variables to configure Cypress and Artemis.
export CYPRESS_baseUrl="http://artemis-app:$artemisPort"
export CYPRESS_adminUsername=$adminUser
export CYPRESS_adminPassword=$adminPassword
export CYPRESS_username='${bamboo.cypress_username_template}'
export CYPRESS_password='${bamboo.cypress_password_template}'
export CYPRESS_allowGroupCustomization=true
export CYPRESS_studentGroupName=artemis-e2etest-students
export CYPRESS_tutorGroupName=artemis-e2etest-tutors
export CYPRESS_editorGroupName=artemis-e2etest-editors
export CYPRESS_instructorGroupName=artemis-e2etest-instructors
export BAMBOO_PLAN_KEY=${bamboo.planKey}
export BAMBOO_BUILD_NUMBER=${bamboo.buildNumber}
export BAMBOO_TOKEN=${bamboo.BAMBOO_PERSONAL_SECRET}

export DATASOURCE_URL="jdbc:mysql://artemis-mysql:3306/Artemis?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC"
export UM_URL="https://jira-prelive.ase.in.tum.de"
export CI_URL="https://bamboo-prelive.ase.in.tum.de"
export SCM_URL="https://bitbucket-prelive.ase.in.tum.de"
export SCM_USER=$bamboo_jira_prelive_admin_user
export SCM_PASSWORD=$bamboo_jira_prelive_admin_password
export UM_USER=$bamboo_jira_prelive_admin_user
export UM_PASSWORD=$bamboo_jira_prelive_admin_password
export CI_USER=$bamboo_jira_prelive_admin_user
export CI_PASSWORD=$bamboo_jira_prelive_admin_password
export CI_AUTH_TOKEN='${bamboo.ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_VALUE_SECRET}'
export CI_TOKEN='${bamboo.ARTEMIS_CONTINUOUS_INTEGRATION_TOKEN_SECRET}'
export SERVER_URL="http://$(hostname):$artemisPort"
export SERVER_PORT=$artemisPort
export ADMIN_USERNAME=$adminUser
export ADMIN_PASSWORD=$adminPassword
export GH_REGISTRY_TOKEN='${bamboo.GH_REGISTRY_TOKEN}'

#TODO: recheck docker compose version and do it in one command
docker-compose -f docker-compose.yml -f docker-compose.coverage.yml pull
docker-compose -f docker-compose.yml -f docker-compose.coverage.yml build --no-cache
docker-compose -f docker-compose.yml -f docker-compose.coverage.yml up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch ../../../../.successful
else
    echo "Not creating success file because the tests failed"
fi
