package de.tum.in.www1.artemis.domain.competency;

import java.net.URI;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "source")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Source extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "uri")
    private URI uri;

}
