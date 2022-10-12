package de.tum.in.www1.artemis.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.*;

/**
 * This class describes the security configuration for SAML2.
 * <p>
 * Since this {@link SAML2Configuration} is annotated with {@link Order} and {@link SecurityConfiguration}
 * is not, this configuration is evaluated first when the SAML2 Profile is active.
 */
// @formatter:off
@Configuration
@Order(1)
@Profile("saml2")
public class SAML2Configuration extends WebSecurityConfigurerAdapter {

    private final Logger log = LoggerFactory.getLogger(SAML2Configuration.class);

    private final SAML2Properties properties;

    /**
     * Constructs a new instance.
     *
     * @param properties The SAML2 properties
     */
    public SAML2Configuration(final SAML2Properties properties) {
        this.properties = properties;

        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Returns the RelyingPartyRegistrationRepository used by SAML2 configuration.
     * <p>
     * The relying parties are configured in the SAML2 properties. A helper method
     * {@link RelyingPartyRegistrations#fromMetadataLocation} extracts the needed information from the given
     * XML metadata file. Optionally X509 Credentials can be supplied to enable encryption.
     *
     * @return the RelyingPartyRegistrationRepository used by SAML2 configuration.
     */
    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        final List<RelyingPartyRegistration> relyingPartyRegistrations = new ArrayList<>();
        // @formatter:off
        for (SAML2Properties.RelyingPartyProperties config : properties.getIdentityProviders()) {
            relyingPartyRegistrations.add(RelyingPartyRegistrations
                .fromMetadataLocation(config.getMetadata())
                .registrationId(config.getRegistrationId())
                .entityId(config.getEntityId())
                .decryptionX509Credentials(credentialsSink -> this.addDecryptionInformation(credentialsSink, config))
                .signingX509Credentials(credentialsSink -> this.addSigningInformation(credentialsSink, config))
                .build());
        }

        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistrations);
    }

    private void addDecryptionInformation(Collection<Saml2X509Credential> credentialsSink, SAML2Properties.RelyingPartyProperties config) {
        if (!this.checkFiles(config)) {
            return;
        }

        try {
            Saml2X509Credential credentials = Saml2X509Credential.decryption(readPrivateKey(config.getKeyFile()), readPublicCert(config.getCertFile()));
            credentialsSink.add(credentials);
        } catch (IOException | CertificateException e) {
            log.error(e.getMessage(), e);
        }
    }


    private void addSigningInformation(Collection<Saml2X509Credential> credentialsSink, SAML2Properties.RelyingPartyProperties config) {
        if (!this.checkFiles(config)) {
            return;
        }

        try {
            Saml2X509Credential credentials = Saml2X509Credential.signing(readPrivateKey(config.getKeyFile()), readPublicCert(config.getCertFile()));
            credentialsSink.add(credentials);
        } catch (IOException | CertificateException e) {
            log.error(e.getMessage(), e);
        }
    }

    private boolean checkFiles(SAML2Properties.RelyingPartyProperties config) {
        if (config.getCertFile() == null || config.getKeyFile() == null || config.getCertFile().isBlank() || config.getKeyFile().isBlank()) {
            log.debug("No Config for SAML2");
            return false;
        }

        File keyFile = new File(config.getKeyFile());
        File certFile = new File(config.getCertFile());

        if (!keyFile.exists() || !certFile.exists()) {
            log.error("Keyfile or Certfile for SAML[{}] does not exist.", config.getRegistrationId());
            return false;
        }

        return true;
    }

    private static X509Certificate readPublicCert(String file) throws IOException, CertificateException {
        try (InputStream inStream = new FileInputStream(file)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(inStream);
        }
    }

    private RSAPrivateKey readPrivateKey(String file) throws IOException {
        // Read PKCS#8 File!
        try (var keyReader = new FileReader(file, StandardCharsets.UTF_8); var pemParser = new PEMParser(keyReader)) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());
            return (RSAPrivateKey) converter.getPrivateKey(privateKeyInfo);
        }
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .requestMatchers()
                // This filter chain is only applied if the URL matches
                // Else the request is filtered by {@link SecurityConfiguration}.
                .antMatchers("/api/saml2")
                .antMatchers("/saml2/**")
                .antMatchers("/login/**")
            .and()
            .csrf()
                // Needed for SAML to work properly
                .disable()
            .authorizeRequests()
                // The request to the api is permitted and checked directly
                // This allows returning a 401 if the user is not logged in via SAML2
                // to notify the client that a login is needed.
                .antMatchers("/api/saml2").permitAll()
                // Every other request must be authenticated. Any request triggers a SAML2
                // authentication flow
                .anyRequest().authenticated()
            .and()
            // Processes the RelyingPartyRegistrationRepository Bean and installs the filters for SAML2
            .saml2Login()
                // Redirect back to the root
                .defaultSuccessUrl("/", true);
        // @formatter:on
    }

}
