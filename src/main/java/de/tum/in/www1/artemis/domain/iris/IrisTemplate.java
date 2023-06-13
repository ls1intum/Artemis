package de.tum.in.www1.artemis.domain.iris;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisTemplate represents a handlebars template for Iris.
 * It is send to the Iris Python server to generate a response.
 */
@Entity
@Table(name = "iris_template")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTemplate extends DomainObject {

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    public IrisTemplate() {

    }

    public IrisTemplate(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String template) {
        this.content = template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        IrisTemplate template = (IrisTemplate) o;
        return Objects.equals(content, template.content);
    }
}
