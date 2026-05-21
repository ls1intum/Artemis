package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;

/**
 * DTO for AuxiliaryRepository.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuxiliaryRepositoryDTO(Long id, String name, String repositoryUri, String checkoutDirectory, String description) {

    /**
     * Creates a DTO from an AuxiliaryRepository entity.
     *
     * @param auxRepo the AuxiliaryRepository entity to convert
     * @return a new AuxiliaryRepositoryDTO with data from the entity
     */
    public static AuxiliaryRepositoryDTO of(AuxiliaryRepository auxRepo) {
        if (auxRepo == null) {
            return null;
        }
        return new AuxiliaryRepositoryDTO(auxRepo.getId(), auxRepo.getName(), auxRepo.getRepositoryUri(), auxRepo.getCheckoutDirectory(), auxRepo.getDescription());
    }

    /**
     * Converts this DTO to an AuxiliaryRepository entity.
     *
     * @return a new AuxiliaryRepository entity with data from this DTO
     */
    public AuxiliaryRepository toEntity() {
        AuxiliaryRepository auxRepo = new AuxiliaryRepository();
        auxRepo.setId(id);
        auxRepo.setName(name);
        auxRepo.setRepositoryUri(repositoryUri);
        auxRepo.setCheckoutDirectory(checkoutDirectory);
        auxRepo.setDescription(description);
        return auxRepo;
    }
}
