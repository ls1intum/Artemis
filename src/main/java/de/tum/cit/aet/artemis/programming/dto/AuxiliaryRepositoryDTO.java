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
     * Returns the same auxiliary repository without the row id, for payloads that are written to a file and read back
     * by another instance. An importer copies the ids onto the repositories of the exercise it creates, and the
     * creation rejects an auxiliary repository that already has one.
     *
     * @return a copy of this DTO with a {@code null} id
     */
    public AuxiliaryRepositoryDTO withoutId() {
        return new AuxiliaryRepositoryDTO(null, name, repositoryUri, checkoutDirectory, description);
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
