package de.tum.in.www1.exerciseapp.repository.search;

import de.tum.in.www1.exerciseapp.domain.Participation;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data ElasticSearch repository for the Participation entity.
 */
public interface ParticipationSearchRepository extends ElasticsearchRepository<Participation, Long> {
}
