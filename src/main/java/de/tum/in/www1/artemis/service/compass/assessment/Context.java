package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.Collections;
import java.util.HashSet;

public class Context {
    private HashSet<Integer> contextElementIDs;

    public static final Context NO_CONTEXT = new Context(-1);

    public Context(int contextElementID) {
        this.contextElementIDs = new HashSet<>(Collections.singletonList(contextElementID));
    }

    public Context(HashSet<Integer> contextElementIDs) {
        this.contextElementIDs = contextElementIDs;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        return ((Context) obj).contextElementIDs.equals(this.contextElementIDs);
    }

    @Override
    public int hashCode () {
        return contextElementIDs.hashCode();
    }
}
