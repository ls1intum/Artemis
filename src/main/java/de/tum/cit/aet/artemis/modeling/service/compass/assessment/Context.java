package de.tum.cit.aet.artemis.modeling.service.compass.assessment;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Context implements Serializable {

    private Set<Integer> contextElementIDs;

    /**
     * Dummy context, placeholder if no context is available
     */
    public static final Context NO_CONTEXT = new Context(-1);

    public Context() {
    }

    public Context(int contextElementID) {
        this.contextElementIDs = ConcurrentHashMap.newKeySet();
        contextElementIDs.add(contextElementID);
    }

    public Context(Set<Integer> contextElementIDs) {
        this.contextElementIDs = contextElementIDs;
    }

    public Set<Integer> getContextElementIDs() {
        return contextElementIDs;
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
    public int hashCode() {
        return Objects.hashCode(getContextElementIDs());
    }
}
