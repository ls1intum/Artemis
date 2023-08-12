package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DomainObjectIdDTO(Long id) {

    public DomainObjectIdDTO(DomainObject domainObject) {
        this(domainObject.getId());
    }
}
