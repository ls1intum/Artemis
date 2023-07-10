package de.tum.in.www1.artemis.repository.iris;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

/**
 * Spring Data repository for the IrisTemplate entity.
 */
public interface IrisTemplateRepository extends JpaRepository<IrisTemplate, Long> {

}
