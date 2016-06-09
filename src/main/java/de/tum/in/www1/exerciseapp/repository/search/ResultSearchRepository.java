package de.tum.in.www1.exerciseapp.repository.search;

import de.tum.in.www1.exerciseapp.domain.Result;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data ElasticSearch repository for the Result entity.
 */
public interface ResultSearchRepository extends ElasticsearchRepository<Result, Long> {
}
