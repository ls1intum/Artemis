package de.tum.in.www1.artemis.web.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing Grading Criteria.
 */
@RestController
@RequestMapping({ GradingCriteriaResource.Endpoints.ROOT })
public class GradingCriteriaResource {

    public static final class Endpoints {

        public static final String ROOT = "/api";

        private Endpoints() {
        }
    }
}
