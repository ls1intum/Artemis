# Setup Sorry Cypress

1. Use the docker compose file (called `sorry-cypress.yml`) in this folder as base.
2. Copy the `sorry-cypress.env` file to the folder, where the compose files is placed. Now change all the values in the env file accordingly. 
  1. Replace the `<insert-minio-access-key>` and `<insert-minio-secret>` with applicable values (e.g random). These values are needed to manage the minio instance. 
  2. Set a random key for the `ALLOWED_KEYS` (by replacing `<insert-cypress-key>`). This key is needed to authorize bamboo against the sorry-cypress dashboard. 
3. Place all the necessary NGINX config files in their locations
  1. Place the `nginx.conf` file from this folder into this path `files/nginx/nginx.conf`
  2. Place a generated `.htpasswd` file into this path `files/nginx/.htpasswd` (e.g. use a online htpasswd generator). This file is used for the basic auth part of the main dashboard.
  3. Place/link a public SSL certificate file in this path `files/nginx/fullchain.pem`
  4. Place/link a private SSL certificate file in this path `files/nginx/privkey.pem`
4. Ensure that all the URLs within the `nginx.conf` and the `sorry-cypress.yml` file are still valid
5. Start the containers by using `docker compose up -f sorry-cypress.yml`
6. Login into the minio dashboard (https://minio.sorry-cypress.ase.cit.tum.de)
7. Create a new user called "sorry-cypress" (Minio Dashboard -> Identity -> Users)
8. Assign this user only the `writeOnly` policy
9. Generate a random access key and a random secret key (e.g by using `openssl rand -base64 24`)
10. Create and set the just generated `ACCESSKEY` and `SECRETKEY` for this user in the `sorry-cypress.env` file (select the user -> Service Accounts --> Create Access Key)
11. Insert the `ACCESSKEY` into the .env file by replacing the `<insert-minio-user-access-key>` value
12. Insert the `SECRETKEY` into the .env file by replacing the `<insert-minio-user-secret>` value
13. Within the bucket settings, set the lifecycle to delete files ("Expiry") after 14 days.
14. Under `Policies` create a new policy called `sorry-cypress` with the content of the `minio-user-policy.json` file. Now assign this policy to the `sorry-cypress` user (under `Identity` -> `Users`). This will allow the `sorry-cypress` user to upload and delete files to/from the bucket. 
15. Recreate the director container, since the new keys need to be applied (e.g with the following commands `docker compose pull -f sorry-cypress.yml` & `docker compose up -f sorry-cypress.yml`)
16. Access the sorry-cypress dashboard (https://sorry-cypress.ase.cit.tum.de) and create two new projects `artemis-mysql` and `artemis-postgresql`. Set the timeout to a reasonable number (e.g 150 min)
17. Now everything should be setup
