package de.tum.cit.aet.artemis.core.util;

// CodeAbility: Added this class for shibboleth login
public enum SamlHeadersUtil {

    EPPN("ajp_eppn"), DISPLAY_NAME("ajp_displayname"), GIVEN_NAME("ajp_givenname"), SN("ajp_sn"), MAIL("ajp_mail"), AFFILIATION("ajp_affiliation");

    public final String header;

    SamlHeadersUtil(String header) {
        this.header = header;
    }
}

