#!/bin/bash

serverUrl=$1

jwtRegex="jwt=((\w|\d|\.|-)*)"

responseHeaders=$(
  curl -X POST http://"$serverUrl"/api/public/authenticate \
    -H "Content-Type: application/json" \
    -d '{"username":"artemis_admin","password":"artemis_admin","rememberMe":true}' \
    -i
)


[[ $responseHeaders =~ $jwtRegex ]]
adminJwt=${BASH_REMATCH[1]}

for i in 1 2 3
do
  curl -X POST http://"$serverUrl"/api/admin/users \
  -H "Content-Type: application/json" \
  -H "Cookie: jwt=${adminJwt};" \
  -d '{"authorities":["ROLE_USER"],"login":"aa0'${i}'aaa","email":"test_user'${i}'@example.com","firstName":"Test","lastName":"User'${i}'","guidedTourSettings":[],"groups":["default"],"password":"test_user_'${i}'_password"}'
done
