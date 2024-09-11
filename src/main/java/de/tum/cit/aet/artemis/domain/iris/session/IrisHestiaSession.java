package de.tum.cit.aet.artemis.domain.iris.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.hestia.CodeHint;

/**
 * An Iris session for a hestia code hint.
 * Currently used to generate descriptions for code hints.
 */
@Entity
@DiscriminatorValue("HESTIA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisHestiaSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private CodeHint codeHint;

    public CodeHint getCodeHint() {
        return codeHint;
    }

    public void setCodeHint(CodeHint codeHint) {
        this.codeHint = codeHint;
    }

    @Override
    public String toString() {
        return "IrisHestiaSession{" + "id=" + getId() + ", codeHint=" + (codeHint == null ? "null" : codeHint.getId()) + '}';
    }
}
