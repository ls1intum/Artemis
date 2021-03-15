package de.tum.in.www1.artemis.config;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

/**
 * This class describes the security configuration for SAML2.
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
    }

    /**
     * Returns the RelyingPartyRegistrationRepository used by SAML2 configuration.
     *
     * @return the RelyingPartyRegistrationRepository used by SAML2 configuration.
     */
    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        final List<RelyingPartyRegistration> relyingPartyRegistrations = new LinkedList<>();
        // @formatter:off
        for (SAML2Properties.RelyingPartyProperties config : properties.getIdentityProviders()) {
            relyingPartyRegistrations.add(RelyingPartyRegistrations
                .fromMetadataLocation(config.getMetadata())
                .registrationId(config.getRegistrationId())
                .entityId(config.getEntityId())
                .signingX509Credentials(c -> this.addSigningInformation(c, config))
                .build());
        }

        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistrations);
    }

    private void addSigningInformation(Collection<Saml2X509Credential> c, SAML2Properties.RelyingPartyProperties config) {
        if (config.getCertFile() == null || config.getKeyFile() == null || config.getCertFile().isBlank() || config.getKeyFile().isBlank()) {
            return;
        }

        File keyFile = new File(config.getKeyFile());
        File certFile = new File(config.getCertFile());

        if (!keyFile.exists() || !certFile.exists()) {
            log.error("Keyfile or Certfile for SAML[" + config.getRegistrationId() + "] does not exist.");
            return;
        }

        Saml2X509Credential relyingPartySigningCredential = null;
        try {
            PrivateKey privateKey = readPrivateKey(keyFile);
            X509Certificate certificate = readPublicCert(certFile);
            relyingPartySigningCredential = new Saml2X509Credential(privateKey, certificate, Saml2X509Credential.Saml2X509CredentialType.SIGNING, Saml2X509Credential.Saml2X509CredentialType.DECRYPTION);
        } catch (IOException | CertificateException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error(e.getMessage(), e);
        }

        if (relyingPartySigningCredential != null)
            c.add(relyingPartySigningCredential);
    }

    private static X509Certificate readPublicCert(File file) throws IOException, CertificateException {
        try (InputStream inStream = new FileInputStream(file)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(inStream);
        }
    }

    private RSAPrivateKey readPrivateKey(File file) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String key = Files.readString(file.toPath(), Charset.defaultCharset());

        String privateKeyPEM = key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replaceAll(System.lineSeparator(), "")
            .replace("-----END PRIVATE KEY-----", "");

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .requestMatchers()
            .antMatchers("/api/saml2")
            .antMatchers("/saml2/**")
            .antMatchers("/login/**")
            .and()
            .csrf()
            .disable()
            .authorizeRequests()
            .antMatchers("/api/saml2").permitAll()
            .anyRequest().authenticated()
            .and()
            .saml2Login()
            .defaultSuccessUrl("/", true);
    }

}
