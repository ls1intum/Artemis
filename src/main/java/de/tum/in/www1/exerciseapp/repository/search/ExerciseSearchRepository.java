package de.tum.in.www1.exerciseapp.repository.search;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data ElasticSearch repository for the Exercise entity.
 */
public interface ExerciseSearchRepository extends ElasticsearchRepository<Exercise, Long> {
}
