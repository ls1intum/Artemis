package de.tum.cit.aet.artemis.core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Describes the security configuration for SAML2.
 */
@Configuration
@Profile(Constants.PROFILE_SAML2)
public class SAML2Configuration {

    private static final Logger log = LoggerFactory.getLogger(SAML2Configuration.class);

    private final SAML2Properties properties;

    /**
     * Constructs a new instance.
     *
     * @param properties The SAML2 properties
     */
    public SAML2Configuration(final SAML2Properties properties) {
        this.properties = properties;
        // TODO: we should describe why this line is actually needed
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
        for (SAML2Properties.RelyingPartyProperties config : properties.getIdentityProviders()) {
            // @formatter:off
            relyingPartyRegistrations.add(RelyingPartyRegistrations
                .fromMetadataLocation(config.getMetadata())
                .registrationId(config.getRegistrationId())
                .entityId(config.getEntityId())
                .decryptionX509Credentials(credentialsSink -> this.addDecryptionInformation(credentialsSink, config))
                .signingX509Credentials(credentialsSink -> this.addSigningInformation(credentialsSink, config))
                .build());
            // @formatter:on
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
        }
        catch (IOException | CertificateException e) {
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
        }
        catch (IOException | CertificateException e) {
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

    /**
     * Since this configuration is annotated with {@link Order} and {@link SecurityConfiguration}
     * is not, this configuration is evaluated first when the SAML2 Profile is active.
     *
     * @param http The Spring http security configurer.
     * @return The configured http security filter chain.
     * @throws Exception Thrown in case Spring detects an issue with the security configuration.
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain saml2FilterChain(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            // This filter chain is only applied if the URL matches
            // Else the request is filtered by {@link SecurityConfiguration}.
            .securityMatcher("/api/public/saml2", "/saml2/**", "/login/**")
            // Needed for SAML to work properly
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // The request to the api is permitted and checked directly
                // This allows returning a 401 if the user is not logged in via SAML2
                // to notify the client that a login is needed.
                .requestMatchers("/api/public/saml2").permitAll()
                // Every other request must be authenticated. Any request triggers a SAML2
                // authentication flow
                .anyRequest().authenticated()
            )
            // Processes the RelyingPartyRegistrationRepository Bean and installs the filters for SAML2
            // Redirect back to the root
            .saml2Login((config) -> config.defaultSuccessUrl("/", true));
        // @formatter:on

        return http.build();
    }
}
