# Cypress: Generate Client Certificates

[Cypress allows to use self-signed client certificates](https://docs.cypress.io/guides/references/client-certificates
) if you also provide a certificate authority.

In order to generate these certificates we use the tool [mkcert](https://github.com/FiloSottile/mkcert),
instead of openssl for instance, as it's easy to use.

## Generate new certificates
The following steps show how to generate new client certificates and the CA files:

```bash
cd ./src/test/cypress/certs
docker run --rm -v ${PWD}:/certs $(docker build -q . ) /certs/generate-certs.sh artemis-nginx artemis.example localhost 127.0.0.1 ::1
```

The CA private key `rootCA-key.pem` doesn't need to be in version control as it's just necessary for new certificates,
but we can also just recreate the whole CA and client certificates.

## Using the client certificates locally
If you want to access the artemis-nginx locally from your browser instead of the cypress container's browser,
you can also install the CA locally on your computer
[by installing mkcert locally and the following steps](https://github.com/FiloSottile/mkcert#installing-the-ca-on-other-systems):

* copy the CA file for instance to `/home/YOURUSER/.local/share/mkcert/rootCA.pem`
* set `$CAROOT` to the directory with the CA file
* run `mkcert -install`
