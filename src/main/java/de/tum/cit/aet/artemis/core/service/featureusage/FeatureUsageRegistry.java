package de.tum.cit.aet.artemis.core.service.featureusage;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.TrackedFeatureRepository;

/**
 * Keeps the inventory of measurable features and resolves a request to the row it belongs to.
 * <p>
 * The endpoint half of the inventory is written once, at startup, from Spring's own mapping table. That is what lets the
 * admin page report features with <b>no</b> usage: a lazily created row can only ever tell you about endpoints somebody
 * already called, which answers "what is popular" but not "what can we delete". Git and background features cannot be
 * enumerated, so those rows are created the first time they are recorded.
 * <p>
 * The startup pass also builds a {@link Method} to feature id map, so the request path needs one map lookup and never
 * touches the database, builds a string, or reads an annotation.
 * <p>
 * Every node runs the same registration. Concurrent first starts race on the unique key, and the loser reads the winner's
 * row, so the outcome is the same either way.
 * <p>
 * This class deliberately holds no reference to any controller type: it reads {@link HandlerMethod}s at runtime.
 * Importing a {@code @RestController} is forbidden by {@code ArchitectureTest.testNoRestControllersImported}.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class FeatureUsageRegistry {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageRegistry.class);

    private static final String ARTEMIS_PACKAGE_PREFIX = "de.tum.cit.aet.artemis.";

    /** The name Spring MVC gives its own {@link RequestMappingHandlerMapping}. See {@link #resolveHandlerMapping()}. */
    private static final String MVC_HANDLER_MAPPING_BEAN_NAME = "requestMappingHandlerMapping";

    /**
     * Verb placeholder for a mapping that declares no HTTP method. An architecture rule forbids method level
     * {@code @RequestMapping}, so in practice this only guards against a mapping registered programmatically.
     */
    private static final String ANY_VERB = "ANY";

    private static final int MAX_MODULE_LENGTH = 32;

    private static final int MAX_IDENTIFIER_LENGTH = 255;

    private static final int MAX_LABEL_LENGTH = 128;

    /** Ids per statement when stamping the inventory as still registered. */
    private static final int REGISTRATION_BATCH_SIZE = 500;

    private final TrackedFeatureRepository trackedFeatureRepository;

    private final FeatureUsageProperties properties;

    /**
     * The handler mapping is looked up from the context inside the event listener rather than injected. A constructor
     * dependency would force the whole MVC infrastructure to be built while this eager bean is created, which works
     * against the deliberately lazy startup.
     */
    private final ApplicationContext applicationContext;

    /**
     * Handler method to feature id. Written once during startup, read on every request afterwards. Keyed on
     * {@link Method} and not on {@link HandlerMethod}, because the mapping hands the interceptor a different
     * {@code HandlerMethod} instance (one with the bean resolved) than the one held in its own registry.
     */
    private final Map<Method, Long> restFeatureIds = new ConcurrentHashMap<>();

    /** Feature id per {@code kind + identifier}, for the features that cannot be enumerated at startup. */
    private final Map<String, Long> lazyFeatureIds = new ConcurrentHashMap<>();

    /** Serializes the first-sighting registration of a git or background feature. See {@link #featureId}. */
    private final Object lazyRegistrationLock = new Object();

    public FeatureUsageRegistry(TrackedFeatureRepository trackedFeatureRepository, FeatureUsageProperties properties, ApplicationContext applicationContext) {
        this.trackedFeatureRepository = trackedFeatureRepository;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * Registers every Artemis endpoint in the inventory and caches the resolved ids for the request path.
     * <p>
     * Called by {@link FeatureUsageStartupListener} once the application is up, rather than from an {@code @EventListener}
     * here, so that this bean stays lazy: making it eager pulled the repository and the JPA infrastructure behind it into
     * the startup dependency graph.
     * <p>
     * Failures are logged and swallowed: usage analysis is a reporting aid, and a server that refuses to finish starting
     * because it could not write its feature inventory would be a far worse outcome than a page with no data.
     */
    public void registerEndpoints() {
        if (!properties.enabled()) {
            log.debug("Feature usage tracking is disabled, skipping the endpoint inventory");
            return;
        }
        try {
            RequestMappingHandlerMapping handlerMapping = resolveHandlerMapping();
            if (handlerMapping == null) {
                log.warn("No RequestMappingHandlerMapping available, feature usage will not track any endpoint");
                return;
            }
            registerEndpoints(handlerMapping);
        }
        catch (Exception e) {
            log.error("Failed to register the feature usage endpoint inventory, REST usage will not be recorded", e);
        }
    }

    /**
     * Resolves Spring MVC's own handler mapping, the one holding the application's controllers.
     * <p>
     * Resolving it by type alone does not work in a running Artemis: Spring Boot Actuator contributes
     * {@code controllerEndpointHandlerMapping}, a second bean of the same type, so a lookup by type fails as ambiguous and
     * the inventory silently stays empty. The MVC mapping always carries the well known bean name declared by
     * {@code WebMvcConfigurationSupport}, so it is addressed by that name, with a lookup by type left as the fallback for a
     * context that does not use the standard configuration.
     *
     * @return the mapping to scan, or null if this context has none
     */
    @Nullable
    private RequestMappingHandlerMapping resolveHandlerMapping() {
        if (applicationContext.containsBean(MVC_HANDLER_MAPPING_BEAN_NAME)) {
            return applicationContext.getBean(MVC_HANDLER_MAPPING_BEAN_NAME, RequestMappingHandlerMapping.class);
        }
        return applicationContext.getBeanProvider(RequestMappingHandlerMapping.class).getIfUnique();
    }

    /**
     * Scans one handler mapping into the inventory. Package-private so a test can run it against the real controller set
     * without having to boot a second application context just to flip the enabled flag.
     *
     * @param handlerMapping the mapping to scan
     */
    void registerEndpoints(RequestMappingHandlerMapping handlerMapping) {
        Map<String, EndpointDescriptor> descriptorsByIdentifier = new LinkedHashMap<>();
        Map<Method, String> identifiersByMethod = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            if (!handlerMethod.getBeanType().getPackageName().startsWith(ARTEMIS_PACKAGE_PREFIX)) {
                continue;
            }
            EndpointDescriptor descriptor = describe(entry.getKey(), handlerMethod);
            if (descriptor == null) {
                continue;
            }
            descriptorsByIdentifier.putIfAbsent(descriptor.identifier(), descriptor);
            identifiersByMethod.put(handlerMethod.getMethod(), descriptor.identifier());
        }

        Map<String, Long> idsByIdentifier = synchronizeInventory(descriptorsByIdentifier.values());
        identifiersByMethod.forEach((method, identifier) -> {
            Long featureId = idsByIdentifier.get(identifier);
            if (featureId != null) {
                restFeatureIds.put(method, featureId);
            }
        });
        log.info("Feature usage inventory ready: {} endpoints tracked across {} modules", restFeatureIds.size(),
                descriptorsByIdentifier.values().stream().map(EndpointDescriptor::module).distinct().count());
    }

    /**
     * Brings the stored inventory in line with the endpoints this version exposes and returns their ids.
     * <p>
     * Rows for endpoints that no longer exist are deliberately kept, because their history is worth more than the space
     * they take: a feature that was removed is exactly the kind of decision this page is meant to support. They are not
     * silently mixed in with the live ones either. Everything still present gets its {@code lastRegisteredAt} advanced, so
     * the report can tell "exists and nobody uses it" from "already gone".
     */
    private Map<String, Long> synchronizeInventory(Iterable<EndpointDescriptor> descriptors) {
        Map<String, TrackedFeature> existingByIdentifier = trackedFeatureRepository.findAll().stream().filter(feature -> feature.getFeatureKind() == FeatureKind.REST)
                .collect(Collectors.toMap(TrackedFeature::getIdentifier, Function.identity(), (first, second) -> first));

        Instant now = Instant.now();
        Map<String, Long> idsByIdentifier = new HashMap<>();
        List<EndpointDescriptor> missing = new ArrayList<>();
        List<Long> stillRegistered = new ArrayList<>();
        for (EndpointDescriptor descriptor : descriptors) {
            TrackedFeature existing = existingByIdentifier.get(descriptor.identifier());
            if (existing == null) {
                missing.add(descriptor);
                continue;
            }
            idsByIdentifier.put(descriptor.identifier(), existing.getId());
            stillRegistered.add(existing.getId());
            if (!Objects.equals(existing.getFeatureLabel(), descriptor.label())) {
                trackedFeatureRepository.updateFeatureLabel(existing.getId(), descriptor.label());
            }
        }
        markStillRegistered(stillRegistered, now);
        if (missing.isEmpty()) {
            return idsByIdentifier;
        }

        try {
            // one batched transaction, which matters on a fresh database where every endpoint is new
            trackedFeatureRepository.saveAll(missing.stream().map(descriptor -> toEntity(descriptor, now)).toList())
                    .forEach(feature -> idsByIdentifier.put(feature.getIdentifier(), feature.getId()));
        }
        catch (DataIntegrityViolationException e) {
            log.debug("Another node registered part of the feature inventory concurrently, falling back to individual registration");
            for (EndpointDescriptor descriptor : missing) {
                Long featureId = persistOrRead(toEntity(descriptor, now));
                if (featureId != null) {
                    idsByIdentifier.put(descriptor.identifier(), featureId);
                }
            }
        }
        return idsByIdentifier;
    }

    /**
     * Stamps the still-existing features in batches, so the {@code IN} list stays a sane size on a codebase with a
     * thousand endpoints.
     */
    private void markStillRegistered(List<Long> featureIds, Instant now) {
        for (int start = 0; start < featureIds.size(); start += REGISTRATION_BATCH_SIZE) {
            trackedFeatureRepository.markStillRegistered(featureIds.subList(start, Math.min(start + REGISTRATION_BATCH_SIZE, featureIds.size())), now);
        }
    }

    /**
     * Returns the feature id of a resolved handler method, or {@code null} if it is not part of the inventory.
     * <p>
     * On the request path, so it does no more than one map lookup.
     *
     * @param method the handler method that served the request
     * @return the id of the matching inventory row, or {@code null} if there is none
     */
    @Nullable
    public Long restFeatureId(Method method) {
        return restFeatureIds.get(method);
    }

    /**
     * Returns the feature id of a git or background feature, registering it on first sighting.
     * <p>
     * Only the first call per feature and node touches the database; everything after that is a map lookup.
     *
     * @param featureKind the namespace, {@link FeatureKind#GIT} or {@link FeatureKind#BACKGROUND}
     * @param module      the Artemis module the feature belongs to
     * @param identifier  the canonical identifier within the namespace
     * @return the id of the matching inventory row, or {@code null} if it could not be registered
     */
    @Nullable
    public Long featureId(FeatureKind featureKind, String module, String identifier) {
        String truncatedIdentifier = truncate(identifier, MAX_IDENTIFIER_LENGTH);
        String cacheKey = featureKind.name() + ' ' + truncatedIdentifier;
        Long cached = lazyFeatureIds.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        // Creation is serialized within this JVM. Unsynchronized, two threads seeing the same feature for the first time
        // both find nothing and both insert, and the loser's insert is rejected by the unique key. That is recovered from,
        // but it logs a database error for what is normal operation, and a monitoring feature that writes alarming lines
        // into the log on a fresh database is its own small problem. Only the first sighting of a feature takes this path;
        // afterwards the lookup above returns without locking. A race between nodes remains possible and is what the
        // unique key and persistOrRead are for.
        synchronized (lazyRegistrationLock) {
            Long created = lazyFeatureIds.get(cacheKey);
            if (created != null) {
                return created;
            }
            Long featureId = trackedFeatureRepository.findByFeatureKindAndIdentifier(featureKind, truncatedIdentifier).map(DomainObject::getId)
                    .orElseGet(() -> persistOrRead(new TrackedFeature(featureKind, truncate(module, MAX_MODULE_LENGTH), truncatedIdentifier, null, Instant.now())));
            if (featureId != null) {
                lazyFeatureIds.put(cacheKey, featureId);
            }
            return featureId;
        }
    }

    /**
     * Inserts a new inventory row, or reads the winner's row when another node inserted the same one first.
     */
    @Nullable
    private Long persistOrRead(TrackedFeature feature) {
        try {
            return trackedFeatureRepository.save(feature).getId();
        }
        catch (DataIntegrityViolationException e) {
            return trackedFeatureRepository.findByFeatureKindAndIdentifier(feature.getFeatureKind(), feature.getIdentifier()).map(DomainObject::getId).orElse(null);
        }
    }

    private static TrackedFeature toEntity(EndpointDescriptor descriptor, Instant firstSeenAt) {
        return new TrackedFeature(FeatureKind.REST, descriptor.module(), descriptor.identifier(), descriptor.label(), firstSeenAt);
    }

    /**
     * Derives what one endpoint contributes to the inventory. Package-private so a unit test can drive it with
     * hand-built mappings, which is where the interesting cases are (legacy aliases, missing class level prefix).
     *
     * @param mappingInfo   the mapping of the endpoint
     * @param handlerMethod the handler method the mapping resolves to
     * @return the descriptor, or {@code null} if the mapping declares no path
     */
    @Nullable
    static EndpointDescriptor describe(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        // A mapping with no path reports one blank pattern rather than none, so blanks have to be filtered rather than
        // just checking for an empty set. Without this, such a mapping would add an inventory row identified by its verb
        // alone.
        Set<String> patterns = mappingInfo.getPatternValues().stream().filter(pattern -> !pattern.isBlank()).collect(Collectors.toSet());
        if (patterns.isEmpty()) {
            return null;
        }
        String identifier = truncate(httpVerb(mappingInfo) + ' ' + canonicalPath(patterns, handlerMethod), MAX_IDENTIFIER_LENGTH);
        return new EndpointDescriptor(identifier, moduleOf(handlerMethod), labelOf(handlerMethod));
    }

    /**
     * Picks the canonical path of a mapping.
     * <p>
     * Some controllers map a canonical prefix plus one or more deprecated legacy aliases in a single
     * {@code @RequestMapping}, where the first entry is the canonical one. Without this, the same feature would be
     * counted under two identifiers and neither row would show its real usage.
     */
    private static String canonicalPath(Set<String> patterns, HandlerMethod handlerMethod) {
        RequestMapping classMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            String canonicalPrefix = withLeadingSlash(classMapping.value()[0]);
            Optional<String> canonical = patterns.stream().filter(pattern -> withLeadingSlash(pattern).startsWith(canonicalPrefix)).min(Comparator.naturalOrder());
            if (canonical.isPresent()) {
                return withoutLeadingSlash(canonical.get());
            }
        }
        // no class level prefix, or none of the patterns sits under it: pick deterministically so the identifier is stable
        return withoutLeadingSlash(patterns.stream().min(Comparator.naturalOrder()).orElseThrow());
    }

    private static String httpVerb(RequestMappingInfo mappingInfo) {
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return ANY_VERB;
        }
        return methods.stream().map(RequestMethod::name).sorted().collect(Collectors.joining(","));
    }

    /**
     * Derives the module from the controller's package. The {@code api/<module>/} path convention is enforced by an
     * architecture rule, so the package and the path agree, and the package is the one of the two that is always present.
     */
    private static String moduleOf(HandlerMethod handlerMethod) {
        String remainder = handlerMethod.getBeanType().getPackageName().substring(ARTEMIS_PACKAGE_PREFIX.length());
        int separator = remainder.indexOf('.');
        return truncate(separator < 0 ? remainder : remainder.substring(0, separator), MAX_MODULE_LENGTH);
    }

    /**
     * Resolves the {@code area/feature} label of an endpoint.
     * <p>
     * A method level {@code @FeatureUsage} wins over the controller's own, so a controller that genuinely serves two
     * features can split them. Every controller is required to carry one, enforced by
     * {@code FeatureUsageAnnotationTest}, so an unlabelled endpoint means a controller was added without deciding which
     * feature it belongs to.
     */
    @Nullable
    private static String labelOf(HandlerMethod handlerMethod) {
        FeatureUsage annotation = handlerMethod.getMethod().getAnnotation(FeatureUsage.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(FeatureUsage.class);
        }
        if (annotation == null) {
            return null;
        }
        String label = annotation.value().strip();
        return label.isEmpty() ? null : truncate(label, MAX_LABEL_LENGTH);
    }

    private static String withLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String withoutLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * What one endpoint contributes to the inventory.
     *
     * @param identifier the canonical verb and path, unique within {@link FeatureKind#REST}
     * @param module     the Artemis module the endpoint belongs to
     * @param label      the {@code @FeatureUsage} label, or {@code null} when the endpoint is unlabelled
     */
    record EndpointDescriptor(String identifier, String module, @Nullable String label) {
    }
}
