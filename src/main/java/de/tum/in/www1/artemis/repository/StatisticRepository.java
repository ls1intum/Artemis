package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Statistic;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the Statistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticRepository extends JpaRepository<Statistic, Long> {

}
