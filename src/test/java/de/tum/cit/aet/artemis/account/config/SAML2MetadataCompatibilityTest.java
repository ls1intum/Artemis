package de.tum.cit.aet.artemis.account.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

/**
 * Guards the OpenSAML/commons-codec compatibility required when loading IdP metadata at startup.
 * XML Schema base64Binary permits whitespace and does not require a particular line length.
 */
class SAML2MetadataCompatibilityTest {

    @ParameterizedTest(name = "certificate line length {0}, separator {1}, indented {2}")
    @CsvSource({ "0, LF, false", "64, LF, false", "64, CRLF, false", "76, LF, false", "76, CRLF, false", "64, LF, true" })
    void loadsVerificationCertificateFromMetadata(int lineLength, String separator, boolean indented) throws Exception {
        X509Certificate certificate;
        // Public, self-signed test fixture; no private key is used or retained.
        try (var input = new ClassPathResource("test-data/saml2/idp-certificate.pem").getInputStream()) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        byte[] lineSeparator = ("CRLF".equals(separator) ? "\r\n" : "\n").getBytes(StandardCharsets.US_ASCII);
        String encodedCertificate = Base64.getMimeEncoder(lineLength, lineSeparator).encodeToString(certificate.getEncoded());
        if (indented) {
            encodedCertificate = "\n    " + encodedCertificate.replace("\n", "\n    ") + "\n";
        }
        String metadata = """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                                     xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                                     entityID="https://idp.example.org">
                    <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                        <md:KeyDescriptor use="signing">
                            <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                        </md:KeyDescriptor>
                        <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                                               Location="https://idp.example.org/sso"/>
                    </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(encodedCertificate);

        var registration = RelyingPartyRegistrations.fromMetadata(new ByteArrayInputStream(metadata.getBytes(StandardCharsets.UTF_8))).registrationId("test-idp").build();

        var assertingParty = registration.getAssertingPartyMetadata();
        assertThat(assertingParty.getEntityId()).isEqualTo("https://idp.example.org");
        assertThat(assertingParty.getSingleSignOnServiceLocation()).isEqualTo("https://idp.example.org/sso");
        assertThat(assertingParty.getSingleSignOnServiceBinding()).isEqualTo(Saml2MessageBinding.REDIRECT);
        assertThat(assertingParty.getVerificationX509Credentials()).singleElement().satisfies(credential -> {
            assertThat(credential.isVerificationCredential()).isTrue();
            assertThat(credential.getCertificate()).isEqualTo(certificate);
        });
    }
}
