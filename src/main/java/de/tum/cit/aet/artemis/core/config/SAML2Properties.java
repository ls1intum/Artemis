package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This class describes SAML2 properties.
 */
@Profile(PROFILE_CORE)
@Component
@ConfigurationProperties("saml2")
public class SAML2Properties {

    /**
     * The name of the regular expression capture group which should match the actual value.
     */
    public static final String ATTRIBUTE_VALUE_EXTRACTION_GROUP_NAME = "value";

    private String usernamePattern;

    private String firstNamePattern;

    private String lastNamePattern;

    private String emailPattern;

    private String registrationNumberPattern;

    private String langKeyPattern;

    private List<RelyingPartyProperties> identityProviders;

    private Set<ExtractionPattern> valueExtractionPatterns = Set.of();

    @PostConstruct
    private void init() {
        final Set<String> extractionKeys = new HashSet<>();

        for (ExtractionPattern pattern : valueExtractionPatterns) {
            final String key = pattern.getKey();

            if (!pattern.isValidPattern()) {
                String message = String.format("The extraction pattern for key '%s' does not contain a capture group with name '%s'!", key, ATTRIBUTE_VALUE_EXTRACTION_GROUP_NAME);
                throw new BeanInitializationException(message);
            }

            if (extractionKeys.contains(key)) {
                throw new BeanInitializationException(String.format("The attribute key '%s' cannot have more than one extraction pattern!", key));
            }

            extractionKeys.add(key);
        }
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
     * @param langKeyPattern The language key pattern
     */
    public void setLangKeyPattern(String langKeyPattern) {
        this.langKeyPattern = langKeyPattern;
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
     * Gets the extraction patterns.
     *
     * @return The extraction patterns.
     */
    public Set<ExtractionPattern> getValueExtractionPatterns() {
        return valueExtractionPatterns;
    }

    /**
     * Sets the extraction patterns.
     *
     * @param valueExtractionPatterns The extraction patterns.
     */
    public void setValueExtractionPatterns(Set<ExtractionPattern> valueExtractionPatterns) {
        this.valueExtractionPatterns = valueExtractionPatterns;
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

    /**
     * Used to define a regular expression with which only a part of an attribute value is extracted.
     */
    public static class ExtractionPattern {

        private String key;

        private String valuePattern;

        /**
         * Checks that this pattern contains a correctly named capture group.
         *
         * @return true, if the pattern can be used to extract parts from attribute values.
         */
        protected boolean isValidPattern() {
            return this.valuePattern.contains(String.format("(?<%s>", ATTRIBUTE_VALUE_EXTRACTION_GROUP_NAME));
        }

        /**
         * Gets the key for which the value extraction should be applied.
         *
         * @return The key for which the value extraction should be applied.
         */
        public String getKey() {
            return key;
        }

        /**
         * Sets the key for which the value extraction should be applied.
         *
         * @param key The key for which the value extraction should be applied.
         */
        public void setKey(String key) {
            this.key = key;
        }

        /**
         * Gets the pattern that defines which part of the value should be extracted.
         *
         * @return The pattern that defines which part of the value should be extracted.
         */
        public String getValuePattern() {
            return valuePattern;
        }

        /**
         * Sets the pattern that defines which part of the value should be extracted.
         *
         * @param valuePattern The pattern that defines which part of the value should be extracted.
         */
        public void setValuePattern(String valuePattern) {
            this.valuePattern = valuePattern;
        }
    }
}
