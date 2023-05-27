package de.tum.in.www1.artemis.domain.iris;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.hestia.CodeHint;

/**
 * An IrisSession represents a conversation between a user and an Artemis bot.
 * Currently, IrisSessions are only used to help students with programming exercises.
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
        return "IrisHestiaSession{" + "id=" + getId() + "codeHint=" + (codeHint == null ? "null" : codeHint.getId()) + '}';
    }
}
