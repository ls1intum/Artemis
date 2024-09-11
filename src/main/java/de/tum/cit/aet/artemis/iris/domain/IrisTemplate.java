package de.tum.cit.aet.artemis.iris.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * An IrisTemplate represents a handlebars template for Iris.
 * It is sent to the Iris Python server to generate a response.
 */
@Entity
@Table(name = "iris_template")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTemplate extends DomainObject {

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /**
     * Empty constructor required for Hibernate and Jackson.
     */
    public IrisTemplate() {
    }

    /**
     * Create a new IrisTemplate with content.
     *
     * @param content the content of the template
     */
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
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        IrisTemplate template = (IrisTemplate) other;
        return Objects.equals(content, template.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), content);
    }
}
