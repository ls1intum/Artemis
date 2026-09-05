package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AssignableTypeFilter;

import de.tum.cit.aet.artemis.account.dto.passkey.PublicKeyCredentialCreationOptionsDTO;
import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService;
import de.tum.cit.aet.artemis.atlas.domain.competency.ContentChangeAccumulator;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentSessionCacheService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentAddressInfo;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.communication.dto.SavedPostDTO;
import de.tum.cit.aet.artemis.core.config.cache.BlobCacheConfiguration;
import de.tum.cit.aet.artemis.core.service.cache.PerNodeCacheEvictionService.PerNodeCacheEviction;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.MapItemEvent;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.QueueItemEvent;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectMessage;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionService.ControlAction;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * Fails when the shape of a type stored in the distributed store changes, so that the change is a decision rather than
 * a discovery.
 *
 * <p>
 * Queue entries, set elements, topic messages and map keys are encoded positionally by Kryo, which carries no schema
 * and no version. A release that reads what another release wrote therefore walks into the wrong bytes rather than
 * getting a clean error, which is what issue #12137 looked like in production. Namespacing by
 * {@link DistributedDataSchema#VERSION} prevents that, but only if somebody remembers to bump the version. This test
 * is the reminder, and plays the part a Liquibase checksum plays for the database. Values returned by methods backed
 * by the distributed Spring cache are discovered automatically, because those maps are part of the same persistent
 * surface even though they are not obtained directly at an application call site.
 *
 * <p>
 * <b>When this test fails</b>, decide whether the change is one an older build could still read:
 * <ul>
 * <li>It cannot (a component added, removed, reordered or retyped): bump {@link DistributedDataSchema#VERSION}, decide
 * in the explicit adjacent migration step whether the affected structure survives the bump, and then
 * update the recorded surface.</li>
 * <li>It can: document why the wire representation remains compatible and update the recorded surface.</li>
 * </ul>
 * Either way the test writes what it found next to the recorded file, so updating it is a review of the diff followed
 * by a copy.
 */
class DistributedDataSurfaceTest {

    /**
     * Where the accepted surface is recorded. Kept as a file rather than a hash so that a failure shows what changed.
     */
    private static final Path RECORDED_SURFACE = Path.of("src", "test", "resources", "config", "distributed-data-surface.txt");

    /**
     * Where a differing surface is written, so the fix is reviewing a diff rather than transcribing by hand.
     */
    private static final Path ACTUAL_SURFACE = Path.of("build", "distributed-data-surface.actual.txt");

    /**
     * The types stored in the distributed structures. Everything they reach is walked, so this only has to name the
     * roots: a build job and its result as they travel through the queues and the processing map, the agent record
     * whose change caused #12137, the key type of the feature toggles, the two records the cluster keeps about its own
     * nodes, the entries the Hyperion, OIDC and Atlas agent caches hold, and the concrete envelopes published through
     * distributed topics and map/queue notification topics.
     */
    private static final List<Class<?>> DECLARED_ROOTS = List.of(BuildJobQueueItem.class, ResultQueueItem.class, BuildAgentInformation.class, Feature.class,
            BuildAgentAddressInfo.class, ClusterNodeInfo.class, HyperionCodeGenerationJobService.JobInfo.class, OIDCExchangeCodeService.ExchangeCodeEntry.class,
            AtlasAgentSessionCacheService.MessagePreviewData.class, ContentChangeAccumulator.class, PublicKeyCredentialCreationOptionsDTO.class, QueueItemEvent.class,
            MapItemEvent.class, PerNodeCacheEviction.class, WebsocketBrokerReconnectMessage.class);

    /**
     * Where the {@link PyrisJob} implementations live. The {@code pyris-job-map} stores them polymorphically, so the
     * walk cannot reach them from a declared type: the map is typed by the interface, and the shape that is encoded is
     * the concrete record's. They are discovered rather than listed so that a job added later is covered without
     * anyone remembering to come back here.
     */
    private static final String PYRIS_JOB_PACKAGE = "de.tum.cit.aet.artemis.iris.service.pyris.job";

    /**
     * Stored types this package cannot name directly, because they are package-private where they are declared.
     * Loaded by name so that they are still covered rather than quietly left out.
     */
    private static final List<String> ROOTS_BY_NAME = List.of("de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService$RunInfo");

    /**
     * Cache annotations that can write a method's return value. {@link Caching} is included because it may wrap either
     * of the other two annotations.
     */
    private static final Set<String> CACHE_VALUE_ANNOTATIONS = Set.of(Cacheable.class.getName(), CachePut.class.getName(), Caching.class.getName());

    /**
     * @return every type stored in a distributed structure, including the concrete jobs reached only polymorphically
     */
    private static List<Class<?>> roots() {
        return Stream
                .of(DECLARED_ROOTS.stream(), ROOTS_BY_NAME.stream().map(DistributedDataSurfaceTest::loadClass), storedPyrisJobs().stream(), distributedCacheValueRoots().stream())
                .flatMap(types -> types).distinct().toList();
    }

    private static List<Class<?>> storedPyrisJobs() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(PyrisJob.class));
        return scanner.findCandidateComponents(PYRIS_JOB_PACKAGE).stream().map(BeanDefinition::getBeanClassName).map(DistributedDataSurfaceTest::loadClass)
                .sorted(Comparator.comparing(Class::getName)).toList();
    }

    /**
     * Discovers values written through Spring's cache abstraction. The default cache manager routes every cache except
     * the three local blob caches to {@code DistributedDataProvider}, so these return values have the same persistence
     * and compatibility requirements as values passed to a distributed map directly.
     *
     * @return every Artemis-owned type that can be stored as a distributed cache value
     */
    private static List<Class<?>> distributedCacheValueRoots() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {

            @Override
            protected boolean isCandidateComponent(MetadataReader metadataReader) {
                // Do not evaluate Artemis feature conditions: this test reads class metadata and needs no application
                // configuration. The default scanner evaluates @Conditional while walking candidate classes.
                var metadata = metadataReader.getAnnotationMetadata();
                return CACHE_VALUE_ANNOTATIONS.stream().anyMatch(annotation -> metadata.hasAnnotation(annotation) || metadata.hasAnnotatedMethods(annotation));
            }

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // Repository cache annotations are declared on interfaces, which the default candidate check excludes.
                return beanDefinition.getMetadata().isIndependent();
            }
        };

        var cacheOperationSource = new AnnotationCacheOperationSource(false);
        return scanner.findCandidateComponents("de.tum.cit.aet.artemis").stream().filter(DistributedDataSurfaceTest::isProductionClass).map(BeanDefinition::getBeanClassName)
                .map(DistributedDataSurfaceTest::loadClass)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()).filter(method -> storesDistributedCacheValue(cacheOperationSource, type, method))
                        .map(Method::getGenericReturnType))
                .flatMap(type -> rawTypesOf(type).stream()).filter(DistributedDataSurfaceTest::isOurs).distinct().sorted(Comparator.comparing(Class::getName)).toList();
    }

    /**
     * The test runtime contains both production and test classes under the Artemis package. Only production
     * annotations describe data that can persist in an installation.
     */
    private static boolean isProductionClass(BeanDefinition beanDefinition) {
        String resource = beanDefinition.getResourceDescription();
        if (resource == null) {
            return true;
        }
        String normalizedResource = resource.replace('\\', '/');
        return !normalizedResource.contains("/classes/java/test/") && !normalizedResource.contains("/classes/kotlin/test/") && !normalizedResource.contains("/test-classes/")
                && !normalizedResource.contains("/out/test/");
    }

    /**
     * @param type the type a sentinel asserts is recorded
     * @return the name as the surface writes it, up to and including the space that ends it, so that a longer name
     *         cannot satisfy the assertion
     */
    private static String named(Class<?> type) {
        return type.getName() + " ";
    }

    private static boolean storesDistributedCacheValue(AnnotationCacheOperationSource source, Class<?> declaringType, Method method) {
        Collection<CacheOperation> operations = source.getCacheOperations(method, declaringType);
        return operations != null && operations.stream().anyMatch(DistributedDataSurfaceTest::isDistributedCacheValueOperation);
    }

    private static boolean isDistributedCacheValueOperation(CacheOperation operation) {
        if (!(operation instanceof CacheableOperation || operation instanceof CachePutOperation)) {
            return false;
        }
        if ("blobCacheManager".equals(operation.getCacheManager())) {
            return false;
        }
        if ("distributedCacheManager".equals(operation.getCacheManager())) {
            return true;
        }
        return operation.getCacheNames().isEmpty() || operation.getCacheNames().stream().anyMatch(name -> !BlobCacheConfiguration.BLOB_CACHE_NAMES.contains(name));
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Found " + name + " on the class path but could not load it", e);
        }
    }

    /**
     * Types whose shape Artemis does not control and cannot change, so walking into them adds noise rather than
     * coverage.
     */
    private static boolean isOurs(Class<?> type) {
        return type.getName().startsWith("de.tum.cit.aet.artemis.");
    }

    @Test
    void testDistributedDataSurfaceIsUnchanged() throws Exception {
        String surface = renderSurface();
        String recorded = Files.exists(RECORDED_SURFACE) ? Files.readString(RECORDED_SURFACE, StandardCharsets.UTF_8) : "";

        // Each name is followed by a space, because every recorded line reads "<kind> <name> serialVersionUID=...". A bare
        // name is a substring of a longer one, so the SavedPost sentinel below went on passing on SavedPostStatus alone
        // after the cached value became a projection, and stopped guarding anything.
        assertThat(surface).as("notification and direct-topic payloads must remain part of the compatibility gate").contains(named(QueueItemEvent.class),
                named(QueueItemEvent.EventType.class), named(MapItemEvent.class), named(MapItemEvent.EventType.class), named(PerNodeCacheEviction.class),
                named(WebsocketBrokerReconnectMessage.class), named(ControlAction.class));
        assertThat(surface).as("values stored through the distributed Spring cache must remain part of the compatibility gate").contains(named(SavedPostDTO.class));

        if (!surface.equals(recorded)) {
            // Written before asserting so that the fix is a reviewed copy rather than a hand edit.
            FileUtils.writeStringToFile(ACTUAL_SURFACE.toFile(), surface, StandardCharsets.UTF_8);
        }

        assertThat(surface).as("""
                The shape of a type stored in the distributed store changed.

                Decide whether an older build could still read it. If it could not, because a component was added, \
                removed, reordered or retyped, bump DistributedDataSchema.VERSION and check whether the affected \
                structure is handled by an explicit adjacent migration step. If it could, document why before updating the recorded surface.

                What was found has been written to %s. Review the diff against %s, then replace it.
                """.formatted(ACTUAL_SURFACE, RECORDED_SURFACE)).isEqualTo(recorded);

        Files.deleteIfExists(ACTUAL_SURFACE);
    }

    /**
     * @return the shape of every stored type, one per line, in a stable order
     */
    private static String renderSurface() {
        Set<Class<?>> visited = new LinkedHashSet<>();
        roots().forEach(root -> collect(root, visited));

        var byName = new TreeMap<String, String>();
        for (Class<?> type : visited) {
            byName.put(type.getName(), describe(type));
        }
        StringBuilder rendered = new StringBuilder();
        rendered.append("# Shape of the types stored in the distributed store. See DistributedDataSurfaceTest.\n");
        rendered.append("schema-version=").append(DistributedDataSchema.VERSION).append('\n');
        byName.values().forEach(line -> rendered.append(line).append('\n'));
        return rendered.toString();
    }

    private static void collect(Class<?> type, Set<Class<?>> visited) {
        if (type == null || type.isPrimitive() || !isOurs(type) || !visited.add(type)) {
            return;
        }
        collect(type.getSuperclass(), visited);
        // A sealed carrier says which types may stand in for it, and a stored value is one of them. Without this, a
        // field typed by the interface would be recorded as an empty shape and the payloads it carries would not be
        // reviewed at all.
        if (type.getPermittedSubclasses() != null) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                collect(permitted, visited);
            }
        }
        for (Type referenced : referencedTypes(type)) {
            for (Class<?> candidate : rawTypesOf(referenced)) {
                collect(candidate, visited);
            }
        }
    }

    private static List<Type> referencedTypes(Class<?> type) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getGenericType).toList();
        }
        return Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getGenericType).toList();
    }

    private static List<Class<?>> rawTypesOf(Type type) {
        List<Class<?>> raw = new ArrayList<>();
        switch (type) {
            case Class<?> clazz -> raw.add(clazz.isArray() ? clazz.getComponentType() : clazz);
            case GenericArrayType array -> raw.addAll(rawTypesOf(array.getGenericComponentType()));
            case ParameterizedType parameterized -> {
                raw.addAll(rawTypesOf(parameterized.getRawType()));
                for (Type argument : parameterized.getActualTypeArguments()) {
                    raw.addAll(rawTypesOf(argument));
                }
            }
            case TypeVariable<?> variable -> Arrays.stream(variable.getBounds()).forEach(bound -> raw.addAll(rawTypesOf(bound)));
            case WildcardType wildcard -> {
                Arrays.stream(wildcard.getUpperBounds()).forEach(bound -> raw.addAll(rawTypesOf(bound)));
                Arrays.stream(wildcard.getLowerBounds()).forEach(bound -> raw.addAll(rawTypesOf(bound)));
            }
            default -> {
                // No other Type implementations occur in a Java method signature.
            }
        }
        return raw;
    }

    /**
     * @param type a stored type
     * @return its encoded shape: what it is, the serialVersionUID an older build would compare against, and its
     *         record components or enum constants in declaration order, since Kryo encodes them positionally
     */
    private static String describe(Class<?> type) {
        String kind = type.isRecord() ? "record" : type.isEnum() ? "enum" : type.isInterface() ? "interface" : "class";
        String members;
        if (type.isEnum()) {
            // Constant order is part of the encoding: Kryo writes an enum by ordinal. The constant name rather than
            // toString(), so that renaming a constant is caught and overriding toString() is not.
            members = Arrays.stream(type.getEnumConstants()).map(constant -> ((Enum<?>) constant).name()).collect(Collectors.joining(","));
        }
        else if (type.isRecord()) {
            members = Arrays.stream(type.getRecordComponents()).map(component -> component.getGenericType().getTypeName() + " " + component.getName())
                    .collect(Collectors.joining(", "));
        }
        else {
            // Sorted by name, because getDeclaredFields() has no specified order and recording it would make the gate
            // depend on the compiler that built the class rather than on the class. The cost is that a plain class
            // stored in the distributed store has its field order unobserved here; every root today is a record or an
            // enum, whose order is specified, and a class root would need a check of its own.
            members = Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).sorted(Comparator.comparing(Field::getName))
                    .map(field -> field.getGenericType().getTypeName() + " " + field.getName()).collect(Collectors.joining(", "));
        }
        return "%s %s serialVersionUID=%s serializable=%s [%s]".formatted(kind, type.getName(), serialVersionUidOf(type), Serializable.class.isAssignableFrom(type), members);
    }

    private static String serialVersionUidOf(Class<?> type) {
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(type);
        return descriptor == null ? "none" : String.valueOf(descriptor.getSerialVersionUID());
    }
}
