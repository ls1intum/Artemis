# Setup Sorry Cypress

1. Use the docker compose file (called `sorry-cypress.yml`) in this folder as base.
2. Replace the `<insert-minio-access-key>` and `<insert-minio-secret>` with applicable values (e.g random). These values are needed to manage the minio instance. 
3. Set a random key for the `ALLOWED_KEYS` (by replacing `<insert-cypress-key>`). This key is needed to authorize bamboo against the sorry-cypress dashboard. 
4. Place all the necessary NGINX config files in their locations
    1. Place the `nginx.conf` file from this folder into this path `files/nginx/nginx.conf`
    2. Place a generated `.htpasswd` file into this path `files/nginx/.htpasswd`
    3. Place/link a public SSL certificate file in this path `files/nginx/fullchain.pem`
    4. Place/link a private SSL certificate file in this path `files/nginx/privkey.pem`
5. Ensure that all the URLs within the `nginx.conf` and the `sorry-cypress.yml` file are still valid
6. Start the containers by using `docker compose up -f sorry-cypress.yml`
7. Login into the minio dashboard (https://minio.sorry-cypress.ase.cit.tum.de)
8. Create a new user called "sorry-cypress" (Minio Dashboard -> Identity -> Users)
9. Assign this user only the `writeOnly` policy
10. Generate a random access key and a random secret key (e.g by using `openssl rand -base64 24`)
11. Create and set the just generated `ACCESSKEY` and `SECRETKEY` for this user (select the user -> Service Accounts --> Create Access Key)
12. Insert the `ACCESSKEY` into the compose file by replacing the `<insert-minio-user-access-key>` value
13. Insert the `SECRETKEY` into the compose file by replacing the `<insert-minio-user-secret>` value
14. Within the bucket settings, set the lifecycle to delete files ("Expiry") after 14 days.
15. Under `Policies` create a new policy called `sorry-cypress` with the content of the `minio-user-policy.json` file. Now assign this policy to the `sorry-cypress` user (under `Identity` -> `Users`). This will allow the `sorry-cypress` user to upload and delete files to/from the bucket. 
16. Recreate the director container, since the new keys need to be applied (e.g with the following commands `docker compose pull -f sorry-cypress.yml` & `docker compose up -f sorry-cypress.yml`)
17. Access the sorry-cypress dashboard (https://sorry-cypress.ase.cit.tum.de) and create two new projects `artemis-mysql` and `artemis-postgresql`. Set the timeout to a reasonable number (e.g 150 min)
18. Now everything should be setup
