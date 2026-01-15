package de.tum.cit.aet.artemis.core.config.weaviate;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_WEAVIATE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.weaviate.schema.IrisSchemaFetcher;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.IrisSchemaFetcher.IrisPropertyDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.IrisSchemaFetcher.IrisSchemaDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateCollectionSchema;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviatePropertyDefinition;
import de.tum.cit.aet.artemis.core.config.weaviate.schema.WeaviateSchemas;
import de.tum.cit.aet.artemis.core.exception.WeaviateSchemaValidationException;

/**
 * Validates Artemis Weaviate schema definitions against the Iris repository schemas.
 * This component runs at startup and can either fail the startup (strict mode)
 * or log warnings (non-strict mode) when schema mismatches are detected.
 */
@Component
@Profile(PROFILE_WEAVIATE)
@Lazy(false)
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
public class WeaviateSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(WeaviateSchemaValidator.class);

    private final WeaviateConfigurationProperties properties;

    private final IrisSchemaFetcher schemaFetcher;

    public WeaviateSchemaValidator(WeaviateConfigurationProperties properties, IrisSchemaFetcher schemaFetcher) {
        this.properties = properties;
        this.schemaFetcher = schemaFetcher;
    }

    /**
     * Validates the Artemis schemas against Iris schemas on startup.
     */
    @PostConstruct
    public void validateSchemas() {
        if (!properties.getSchemaValidation().isEnabled()) {
            log.info("Weaviate schema validation is disabled");
            return;
        }

        log.info("Starting Weaviate schema validation against Iris repository...");

        try {
            Map<String, IrisSchemaDefinition> irisSchemas = schemaFetcher.fetchAllSchemas();
            List<String> validationErrors = new ArrayList<>();

            for (WeaviateCollectionSchema artemisSchema : WeaviateSchemas.ALL_SCHEMAS) {
                IrisSchemaDefinition irisSchema = irisSchemas.get(artemisSchema.collectionName());

                if (irisSchema == null) {
                    validationErrors.add(String.format("Collection '%s' exists in Artemis but not found in Iris schemas", artemisSchema.collectionName()));
                    continue;
                }

                List<String> collectionErrors = validateCollection(artemisSchema, irisSchema);
                validationErrors.addAll(collectionErrors);
            }

            // Check for collections in Iris that are not in Artemis
            Set<String> artemisCollections = WeaviateSchemas.ALL_SCHEMAS.stream().map(WeaviateCollectionSchema::collectionName).collect(Collectors.toSet());

            for (String irisCollection : irisSchemas.keySet()) {
                if (!artemisCollections.contains(irisCollection)) {
                    validationErrors.add(String.format("Collection '%s' exists in Iris but not defined in Artemis schemas", irisCollection));
                }
            }

            handleValidationResult(validationErrors);

        }
        catch (IrisSchemaFetcher.IrisSchemaFetchException e) {
            handleFetchError(e);
        }
    }

    /**
     * Validates a single collection schema against its Iris counterpart.
     *
     * @param artemisSchema the Artemis schema definition
     * @param irisSchema    the Iris schema definition
     * @return list of validation errors for this collection
     */
    private List<String> validateCollection(WeaviateCollectionSchema artemisSchema, IrisSchemaDefinition irisSchema) {
        List<String> errors = new ArrayList<>();
        String collectionName = artemisSchema.collectionName();

        Map<String, WeaviatePropertyDefinition> artemisProps = artemisSchema.getPropertiesAsMap();
        Map<String, IrisPropertyDefinition> irisProps = irisSchema.properties().stream().collect(Collectors.toMap(IrisPropertyDefinition::name, p -> p));

        // Check for properties in Artemis not in Iris
        for (String propName : artemisProps.keySet()) {
            if (!irisProps.containsKey(propName)) {
                errors.add(String.format("[%s] Property '%s' exists in Artemis but not in Iris", collectionName, propName));
            }
        }

        // Check for properties in Iris not in Artemis
        for (String propName : irisProps.keySet()) {
            if (!artemisProps.containsKey(propName)) {
                errors.add(String.format("[%s] Property '%s' exists in Iris but not in Artemis", collectionName, propName));
            }
        }

        // Check for property type and indexing mismatches
        for (Map.Entry<String, WeaviatePropertyDefinition> entry : artemisProps.entrySet()) {
            String propName = entry.getKey();
            WeaviatePropertyDefinition artemisProp = entry.getValue();
            IrisPropertyDefinition irisProp = irisProps.get(propName);

            if (irisProp != null) {
                // Check data type
                if (!artemisProp.dataType().getWeaviateName().equalsIgnoreCase(irisProp.dataType())) {
                    errors.add(String.format("[%s] Property '%s' has different data types: Artemis=%s, Iris=%s", collectionName, propName, artemisProp.dataType().getWeaviateName(),
                            irisProp.dataType()));
                }

                // Check searchable indexing
                if (artemisProp.indexSearchable() != irisProp.indexSearchable()) {
                    errors.add(String.format("[%s] Property '%s' has different indexSearchable settings: Artemis=%s, Iris=%s", collectionName, propName,
                            artemisProp.indexSearchable(), irisProp.indexSearchable()));
                }

                // Check filterable indexing
                if (artemisProp.indexFilterable() != irisProp.indexFilterable()) {
                    errors.add(String.format("[%s] Property '%s' has different indexFilterable settings: Artemis=%s, Iris=%s", collectionName, propName,
                            artemisProp.indexFilterable(), irisProp.indexFilterable()));
                }
            }
        }

        return errors;
    }

    /**
     * Handles the validation result based on strict mode setting.
     *
     * @param errors the list of validation errors
     */
    private void handleValidationResult(List<String> errors) {
        if (errors.isEmpty()) {
            log.info("Weaviate schema validation passed: Artemis schemas match Iris schemas");
            return;
        }

        boolean strictMode = properties.getSchemaValidation().isStrict();

        if (strictMode) {
            log.error("Weaviate schema validation failed with {} error(s). Server startup will be aborted.", errors.size());
            throw new WeaviateSchemaValidationException("Weaviate schema validation failed: Artemis schemas do not match Iris schemas", errors, true);
        }
        else {
            log.warn("Weaviate schema validation found {} issue(s), but strict mode is disabled. Continuing with warnings...", errors.size());
            for (String error : errors) {
                log.warn("Schema mismatch: {}", error);
            }
        }
    }

    /**
     * Handles errors that occur while fetching Iris schemas.
     *
     * @param e the fetch exception
     */
    private void handleFetchError(IrisSchemaFetcher.IrisSchemaFetchException e) {
        boolean strictMode = properties.getSchemaValidation().isStrict();

        if (strictMode) {
            log.error("Failed to fetch Iris schemas for validation. Server startup will be aborted.", e);
            throw new WeaviateSchemaValidationException("Failed to fetch Iris schemas for validation: " + e.getMessage(),
                    List.of("Could not connect to Iris schema repository: " + e.getMessage()), true);
        }
        else {
            log.warn("Failed to fetch Iris schemas for validation, but strict mode is disabled. Continuing without validation...", e);
        }
    }
}
