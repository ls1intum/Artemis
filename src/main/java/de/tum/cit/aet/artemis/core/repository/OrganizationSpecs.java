package de.tum.cit.aet.artemis.core.repository;

import java.util.Arrays;

import jakarta.persistence.criteria.Predicate;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.Organization_;

/**
 * Specifications for filtering and querying Organization entities
 */
public class OrganizationSpecs {

    /**
     * Creates the specification to match the provided search term within the
     * organization’s
     * name, short name, and email pattern attributes.
     *
     * @param searchTerm term to match
     * @return specification used to chain database operations
     */
    @NonNull
    public static Specification<Organization> getSearchTermSpecification(String searchTerm) {
        String extendedSearchTerm = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> {
            String[] columns = { Organization_.NAME, Organization_.SHORT_NAME, Organization_.EMAIL_PATTERN };
            Predicate[] predicates = Arrays.stream(columns).map(column -> criteriaBuilder.like(root.get(column), extendedSearchTerm)).toArray(Predicate[]::new);

            return criteriaBuilder.or(predicates);
        };
    }

}
