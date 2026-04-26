package de.tum.cit.aet.artemis.athena.domain;

public enum AthenaModuleMode {

    PRELIMINARY, GRADED;

    public String getReadableString() {
        return name().toLowerCase();
    }
}
