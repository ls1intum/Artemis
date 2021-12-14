package de.tum.in.www1.artemis.domain;

/**
 * Internal users were registered manually in Artemis
 * LTI users were automatically created in artemis
 * LDAP users are managed in an external user management tool
 */
public enum UserType {
    INTERNAL, LTI, LDAP
}
