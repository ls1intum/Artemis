package de.tum.in.www1.artemis.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * This class describes SAML2 properties.
 */
@Component
@ConfigurationProperties("saml2")
public class SAML2Properties {

    private String usernamePattern;

    private String firstNamePattern;

    private String lastNamePattern;

    private String emailPattern;

    private String registrationNumberPattern;

    private boolean enablePassword;

    private List<RelyingPartyProperties> identityProviders;

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
     * Returns true if an app passwort can be set.
     *
     * @return true if an app passwort can be set.
     */
    public boolean getEnablePassword() {
        return enablePassword;
    }

    /**
     * Sets if an app passwort can be set.
     *
     * @param enablePassword true, true if an app passwort can be set
     */
    public void setEnablePassword(boolean enablePassword) {
        this.enablePassword = enablePassword;
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
