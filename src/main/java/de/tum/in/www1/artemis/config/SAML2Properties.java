package de.tum.in.www1.artemis.config;

import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * This class describes SAML2 properties.
 */
@Component
@ConfigurationProperties("saml2")
public class SAML2Properties {

    /**
     * The name of the capture group that is used to extract the registration number from the value of the SAML2 attribute defined by {@link #getRegistrationNumberPattern()}.
     */
    public static final String REGISTRATION_NUMBER_EXTRACTION_GROUP_NAME = "regNum";

    private String usernamePattern;

    private String firstNamePattern;

    private String lastNamePattern;

    private String emailPattern;

    private String registrationNumberPattern;

    private String langKeyPattern;

    private Optional<String> registrationNumberExtractionKeyPattern = Optional.empty();

    private Optional<String> registrationNumberExtractionValuePattern = Optional.empty();

    private List<RelyingPartyProperties> identityProviders;

    @PostConstruct
    private void init() {
        if (isAtLeastOneExtractionPatternPresent() && !areBothExtractionPatternsPresent()) {
            throw new BeanInitializationException(
                    "Options 'saml2.registration-number-extraction-key-pattern' and 'saml2.registration-number-extraction-value-pattern' need to be present at the same time!");
        }

        registrationNumberExtractionValuePattern.ifPresent(pattern -> {
            if (!pattern.contains(String.format("(?<%s>", REGISTRATION_NUMBER_EXTRACTION_GROUP_NAME))) {
                String message = String.format(
                        "The regex that should be used to extract the registration number from the SAML2 attribute has to contain a named capture group '%s'!",
                        REGISTRATION_NUMBER_EXTRACTION_GROUP_NAME);
                throw new BeanInitializationException(message);
            }
        });
    }

    private boolean isAtLeastOneExtractionPatternPresent() {
        return registrationNumberExtractionKeyPattern.isPresent() || registrationNumberExtractionValuePattern.isPresent();
    }

    private boolean areBothExtractionPatternsPresent() {
        return registrationNumberExtractionKeyPattern.isPresent() && registrationNumberExtractionValuePattern.isPresent();
    }

    /**
     * Gets the username pattern.
     *
     * @return the username pattern.
     */
    public String getUsernamePattern() {
        return usernamePattern;
    }

    /**
     * Sets the username pattern.
     *
     * @param usernamePattern the username pattern
     */
    public void setUsernamePattern(String usernamePattern) {
        this.usernamePattern = usernamePattern;
    }

    /**
     * Gets the first name pattern.
     *
     * @return the first name pattern.
     */
    public String getFirstNamePattern() {
        return firstNamePattern;
    }

    /**
     * Sets the first name pattern.
     *
     * @param firstNamePattern the first name pattern
     */
    public void setFirstNamePattern(String firstNamePattern) {
        this.firstNamePattern = firstNamePattern;
    }

    /**
     * Gets the last name pattern.
     *
     * @return the last name pattern.
     */
    public String getLastNamePattern() {
        return lastNamePattern;
    }

    /**
     * Sets the last name pattern.
     *
     * @param lastNamePattern the last name pattern
     */
    public void setLastNamePattern(String lastNamePattern) {
        this.lastNamePattern = lastNamePattern;
    }

    /**
     * Gets the email pattern.
     *
     * @return the email pattern.
     */
    public String getEmailPattern() {
        return emailPattern;
    }

    /**
     * Sets the email pattern.
     *
     * @param emailPattern the email pattern
     */
    public void setEmailPattern(String emailPattern) {
        this.emailPattern = emailPattern;
    }

    /**
     * Gets the registration number pattern.
     *
     * @return the registration number pattern.
     */
    public String getRegistrationNumberPattern() {
        return registrationNumberPattern;
    }

    /**
     * Sets the registration number pattern.
     *
     * @param registrationNumberPattern The registration number pattern
     */
    public void setRegistrationNumberPattern(String registrationNumberPattern) {
        this.registrationNumberPattern = registrationNumberPattern;
    }

    /**
     * Gets the language key pattern.
     *
     * @return The language key pattern.
     */
    public String getLangKeyPattern() {
        return langKeyPattern;
    }

    /**
     * Sets the language key pattern.
     *
     * @param langKeyPattern  The language key pattern
     */
    public void setLangKeyPattern(String langKeyPattern) {
        this.langKeyPattern = langKeyPattern;
    }

    /**
     * Gets the regular expression that defines how the attribute is named where the registration number has to be extracted with {@link #getRegistrationNumberExtractionValuePattern()}.
     * @return The regular expression the attribute key should match.
     */
    public Optional<String> getRegistrationNumberExtractionKeyPattern() {
        return registrationNumberExtractionKeyPattern;
    }

    /**
     * Sets the regular expression that defines how the attribute is named where the registration number has to be extracted with {@link #getRegistrationNumberExtractionValuePattern()}.
     * @param registrationNumberExtractionKeyPattern The regular expression of the attribute key containing the registration number.
     */
    public void setRegistrationNumberExtractionKeyPattern(Optional<String> registrationNumberExtractionKeyPattern) {
        this.registrationNumberExtractionKeyPattern = registrationNumberExtractionKeyPattern;
    }

    /**
     * Gets the regular expression that should be used to extract the registration number from the value of the SAML2 attribute {@link #getRegistrationNumberPattern()}.
     * @return The regular expression to be used for the extraction.
     */
    public Optional<String> getRegistrationNumberExtractionValuePattern() {
        return registrationNumberExtractionValuePattern;
    }

    /**
     * Gets the regular expression that should be used to extract the registration number from the SAML2 attribute.
     *
     * The pattern has to contain a capture group named {@link #REGISTRATION_NUMBER_EXTRACTION_GROUP_NAME}.
     * @param registrationNumberExtractionValuePattern The regular expression to be used for the extraction.
     */
    public void setRegistrationNumberExtractionValuePattern(Optional<String> registrationNumberExtractionValuePattern) {
        this.registrationNumberExtractionValuePattern = registrationNumberExtractionValuePattern;
    }

    /**
     * Gets the identity providers.
     *
     * @return the identity providers.
     */
    public List<RelyingPartyProperties> getIdentityProviders() {
        return identityProviders;
    }

    /**
     * Sets the identity providers.
     *
     * @param identityProviders The identity providers
     */
    public void setIdentityProviders(List<RelyingPartyProperties> identityProviders) {
        this.identityProviders = identityProviders;
    }

    /**
     * This class describes a relying party configuration.
     */
    public static class RelyingPartyProperties {

        private String metadata;

        private String registrationId;

        private String entityId;

        private String certFile;

        private String keyFile;

        /**
         * Gets the url or path to a metadata XML config.
         *
         * @return the url or path to a metadata XML config.
         */
        public String getMetadata() {
            return metadata;
        }

        /**
         * Gets the registration id.
         *
         * @return the registrationid.
         */
        public String getRegistrationId() {
            return registrationId;
        }

        /**
         * Gets the entity id.
         *
         * @return the entityid.
         */
        public String getEntityId() {
            return entityId;
        }

        /**
         * Returns the Certificate File for signatures from this relying party.
         *
         * @return the certificate file or blank/null if no certificate will be used
         */
        public String getCertFile() {
            return certFile;
        }

        /**
         * Returns the Key File for signatures/encryption from this relying party.
         *
         * @return the key file or blank/null if no certificate will be used
         */
        public String getKeyFile() {
            return keyFile;
        }

        /**
         * Sets the url or path to a metadata XML config.
         *
         * @param metadata the url or path to a metadata XML config.
         */
        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }

        /**
         * Sets the registration identifier.
         *
         * @param registrationId The registration identifier
         */
        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        /**
         * Sets the entity identifier.
         *
         * @param entityId The entity identifier
         */
        public void setEntityId(String entityId) {
            this.entityId = entityId;
        }

        /**
         * Sets the certificate file for this relying party.
         *
         * @param certFile the certificate file
         */
        public void setCertFile(String certFile) {
            this.certFile = certFile;
        }

        /**
         * Sets the key file for this relying party.
         *
         * @param keyFile the key file
         */
        public void setKeyFile(String keyFile) {
            this.keyFile = keyFile;
        }
    }
}
