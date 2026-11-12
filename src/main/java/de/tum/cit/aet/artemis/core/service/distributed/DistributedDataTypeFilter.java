package de.tum.cit.aet.artemis.core.service.distributed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides which types may be created when a value is read from the distributed data store.
 *
 * <p>
 * Both distributed data providers read stored values with Java serialization or Kryo, and both resolve the class names
 * found in the stored bytes. Without a filter, whatever a store contains decides which classes are instantiated on the
 * reading node. This filter limits that to the types Artemis stores: its own classes, the {@code java.util},
 * {@code java.time} and {@code java.math} types its records are made of, a few {@code java.lang} value types, and the
 * Spring Security WebAuthn value types referenced by the passkey options. Every other class name is rejected before the
 * class is loaded and a message is logged.
 *
 * <p>
 * The filter only restricts which classes are read. It does not change what is written, so stored data and the
 * {@link DistributedDataSchema#VERSION} stay as they are.
 *
 * <p>
 * An operator can extend the default list with {@code artemis.distributed-data.additional-allowed-types}: a comma
 * separated list in which an entry ending with a dot allows every type below that prefix, and any other entry allows
 * exactly one class.
 *
 * <p>
 * Provider specific classes (the ones Hazelcast and Bucket4j serialize themselves) are not part of the default list;
 * the provider adds them through {@link #withProviderTypes(Set, List)}.
 */
public final class DistributedDataTypeFilter {

    private static final Logger log = LoggerFactory.getLogger(DistributedDataTypeFilter.class);

    /**
     * Classes that are allowed by their exact name: the {@code java.lang} value types and exceptions a stored record can
     * contain, so that classes such as {@code java.lang.Process} stay rejected, and the two markers of Spring's cache
     * abstraction (a cached {@code null} and a composite cache key).
     */
    private static final Set<String> DEFAULT_CLASSES = Set.of("java.io.Serializable", "java.lang.Object", "java.lang.String", "java.lang.Boolean", "java.lang.Byte",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character", "java.lang.Number", "java.lang.Enum",
            "java.lang.Record", "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException", "java.lang.Error", "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException", "java.lang.StackTraceElement", "java.lang.invoke.SerializedLambda", "org.springframework.cache.support.NullValue",
            "org.springframework.cache.interceptor.SimpleKey");

    /**
     * Prefixes that are allowed as a whole: Artemis itself, the JDK collection, time and math types, and the WebAuthn
     * value types a passkey registration option refers to.
     */
    private static final List<String> DEFAULT_PREFIXES = List.of("de.tum.cit.aet.artemis.", "java.util.", "java.time.", "java.math.",
            "org.springframework.security.web.webauthn.api.");

    private final Set<String> classes;

    private final List<String> prefixes;

    private DistributedDataTypeFilter(Set<String> classes, List<String> prefixes) {
        this.classes = Set.copyOf(classes);
        this.prefixes = List.copyOf(prefixes);
    }

    /**
     * @param additionalTypes the operator configured entries, see the class documentation; may be {@code null}
     * @return a filter with the default list plus the configured entries
     */
    public static DistributedDataTypeFilter withAdditional(List<String> additionalTypes) {
        return new DistributedDataTypeFilter(DEFAULT_CLASSES, DEFAULT_PREFIXES).withProviderTypes(Set.of(), additionalTypes);
    }

    /**
     * @param providerClasses exact class names the provider needs to read
     * @param providerEntries further entries in the configuration syntax, where a trailing dot means a prefix; may be {@code null}
     * @return a copy of this filter that additionally allows the given types
     */
    public DistributedDataTypeFilter withProviderTypes(Set<String> providerClasses, List<String> providerEntries) {
        Set<String> allClasses = new LinkedHashSet<>(classes);
        allClasses.addAll(providerClasses);
        List<String> allPrefixes = new ArrayList<>(prefixes);
        if (providerEntries != null) {
            for (String entry : providerEntries) {
                String trimmed = entry == null ? "" : entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.endsWith(".")) {
                    allPrefixes.add(trimmed);
                }
                else {
                    allClasses.add(trimmed);
                }
            }
        }
        return new DistributedDataTypeFilter(allClasses, allPrefixes);
    }

    /**
     * @return the exact class names this filter allows
     */
    public Set<String> allowedClasses() {
        return classes;
    }

    /**
     * @return the name prefixes this filter allows
     */
    public List<String> allowedPrefixes() {
        return prefixes;
    }

    /**
     * Checks a class name as it appears in a serialization stream. Array types are allowed when their element type is.
     *
     * @param className the binary class name, for example {@code java.util.ArrayList} or {@code [Ljava.lang.String;}
     * @return whether a value of that type may be read
     */
    public boolean isAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String name = className;
        if (name.charAt(0) == '[') {
            int start = 0;
            while (start < name.length() && name.charAt(start) == '[') {
                start++;
            }
            if (start == name.length()) {
                return false;
            }
            if (name.charAt(start) != 'L') {
                // an array of a primitive type, such as [B or [[I
                return name.length() == start + 1;
            }
            if (!name.endsWith(";") || name.length() <= start + 2) {
                return false;
            }
            name = name.substring(start + 1, name.length() - 1);
        }
        if (classes.contains(name)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects a class name that is not allowed.
     *
     * @param className the binary class name
     * @throws SecurityException if the class may not be read from the distributed data store
     */
    public void assertAllowed(String className) {
        if (!isAllowed(className)) {
            log.warn("Rejected reading type '{}' from the distributed data store because it is not on the list of types Artemis stores. "
                    + "If this type is stored on purpose, add it to 'artemis.distributed-data.additional-allowed-types'.", className);
            throw new SecurityException("Type " + className + " is not allowed in the distributed data store");
        }
    }

    /**
     * Resolves class names for a deserializer and refuses the ones that are not allowed. Reading a stream resolves a
     * class through this loader before it is instantiated, so a rejected name never reaches class loading.
     */
    public static final class FilteringClassLoader extends ClassLoader {

        private final DistributedDataTypeFilter filter;

        /**
         * @param parent the loader that finds the classes that are allowed
         * @param filter the types that may be resolved
         */
        public FilteringClassLoader(ClassLoader parent, DistributedDataTypeFilter filter) {
            super(parent);
            this.filter = filter;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            filter.assertAllowed(name);
            return super.loadClass(name, resolve);
        }
    }
}
