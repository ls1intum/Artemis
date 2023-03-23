package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.service.FileService;

@Entity
@Table(name = "slide")
@DiscriminatorValue("S")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private String prevLink;

    @Column(name = "name")
    private String name;

    @Column(name = "jhi_link")
    private String link;

    @ManyToOne
    @JoinColumn(name = "attachment_unit_id")
    private AttachmentUnit attachmentUnit;
}
