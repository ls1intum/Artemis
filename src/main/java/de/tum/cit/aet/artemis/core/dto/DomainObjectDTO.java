package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DomainObjectDTO(Long id) {

    public static DomainObjectDTO of(DomainObject domainObject) {
        return new DomainObjectDTO(domainObject.getId());
    }

}
