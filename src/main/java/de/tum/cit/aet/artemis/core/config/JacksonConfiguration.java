package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. The Jackson modules must be
// available when Spring Boot's JacksonAutoConfiguration creates the JsonMapper. With @Lazy, the modules
// are not registered, causing "No _valueDeserializer assigned" errors when deserializing nested entities.
public class JacksonConfiguration {

    /**
     * Support for Java date and time API (Jackson 2 only).
     *
     * @return the corresponding Jackson module.
     * @deprecated Jackson 3 has java.time support built into jackson-databind. Removed together with the Jackson 2 mapper.
     */
    @Deprecated(since = "8.4.0", forRemoval = true)
    @Bean
    public com.fasterxml.jackson.datatype.jsr310.JavaTimeModule javaTimeModule() {
        return new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
    }

    /**
     * Support for Hibernate types in the Jackson 2 mapper.
     *
     * @return the configured Jackson 2 Hibernate7Module
     * @deprecated superseded by {@link #hibernateJacksonModule()}. Removed together with the Jackson 2 mapper.
     */
    @Deprecated(since = "8.4.0", forRemoval = true)
    @Bean
    public com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module hibernateModule() {
        var module = new com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module();
        module.enable(com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module.Feature.REPLACE_PERSISTENT_COLLECTIONS);
        module.enable(com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
    }

    /**
     * Support for Hibernate types in Jackson.
     * <p>
     * Configures the module to safely handle lazy-loaded proxies:
     * <ul>
     * <li>{@code REPLACE_PERSISTENT_COLLECTIONS} converts Hibernate collections to standard
     * Java collections before serialization, so uninitialized proxies become empty
     * collections instead of triggering {@code LazyInitializationException}.</li>
     * <li>{@code WRITE_MISSING_ENTITIES_AS_NULL} serializes unloaded entity proxies
     * ({@code @ManyToOne}, {@code @OneToOne}) as {@code null}.</li>
     * </ul>
     * This is required because {@code spring.jpa.open-in-view} is {@code false}, meaning
     * the Hibernate session is closed before Jackson serializes the response.
     * <p>
     * Spring Boot picks up every {@link tools.jackson.databind.JacksonModule} bean and registers it on both the
     * {@code JsonMapper} and the {@code XmlMapper} it builds.
     *
     * @return the configured Hibernate7Module
     */
    @Bean
    public Hibernate7Module hibernateJacksonModule() {
        Hibernate7Module module = new Hibernate7Module();
        module.enable(Hibernate7Module.Feature.REPLACE_PERSISTENT_COLLECTIONS);
        module.enable(Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
    }

    /**
     * Restores the Jackson 2 value of every default that would otherwise change the JSON on the wire.
     * <p>
     * Jackson 3 changed a number of defaults. Most of them are improvements Artemis keeps — notably
     * {@code SORT_PROPERTIES_ALPHABETICALLY} (deterministic responses and course archives),
     * {@code FAIL_ON_TRAILING_TOKENS} (stricter parsing) and {@code FAIL_ON_EMPTY_BEANS} (an unresolvable
     * Hibernate proxy serializes as {@code {}} rather than throwing). The ones below are pinned because they
     * would silently change the REST contract or how request bodies bind, which is not something a dependency
     * migration should do.
     * <p>
     * Every pin is meant to be temporary. Each carries a TODO naming what has to happen before it can go, because
     * dropping one is an API change that needs the client migrated in the same release rather than a config tweak.
     * {@code JacksonMapperParityTest} is what tells you whether a pin still matters: remove the pin, run it, and the
     * fixture that fails is the payload the change would alter.
     *
     * @return the customizer applied to the auto-configured JsonMapper builder
     */
    @Bean
    public JsonMapperBuilderCustomizer artemisJacksonDefaultsCustomizer() {
        return builder -> builder
                // Jackson 3 enables this. Artemis DTOs use primitive components in 395 files; a client sending an
                // explicit null for an int/boolean would start getting a 400 instead of the zero value it used to.
                // TODO: adopting the Jackson 3 default here would turn "null for a primitive" from a silent zero into
                // a 400, which is the better contract. It needs an audit of which input DTOs actually receive null
                // from the client — boxed types where null is meaningful, primitives where it is a client bug — and
                // the boxed ones converted first. Worth doing: it removes a class of silently-wrong-zero bugs.
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                // Jackson 3 disables these. A collection or map whose only accessor is a getter would silently stop
                // being populated, and final fields would stop being written.
                // TODO: both Jackson 3 defaults are the safer behaviour — they stop Jackson writing through accessors
                // that were never meant as mutators. Adopting them means giving the affected entities real setters.
                // Find them by removing the two pins and running the server test suite; the deserialization failures
                // are the complete list.
                .enable(MapperFeature.USE_GETTERS_AS_SETTERS).enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                // Jackson 3 enables these. Seven Artemis enums override toString() with a different value than name()
                // (RepositoryType returns "solution" where name() returns "SOLUTION", Role returns "ROLE_ADMIN"),
                // so switching to toString() would rename those values on the wire.
                // TODO: the clean fix is to stop relying on the name()/toString() distinction entirely — annotate the
                // seven enums with @JsonValue so their wire format is explicit and independent of this setting, then
                // drop both pins. @JsonValue is already used in nine places, so the pattern is established.
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING).disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                // Note: the date/time defaults need no pin. WRITE_DURATIONS_AS_TIMESTAMPS and WRITE_DATES_AS_TIMESTAMPS
                // read as changes in the raw Jackson default tables, but Spring Boot already disables the latter for
                // Jackson 2, so both mappers write ISO-8601. ONE_BASED_MONTHS only affects java.time.Month and
                // MonthDay, which Artemis never serializes — the calendar module uses YearMonth, which is unaffected.
                // JacksonMapperParityTest covers both.
                // Jackson 3 stops stripping trailing zeroes, so a BigDecimal read into a JsonNode renders differently.
                // TODO: only one production file imports BigDecimal, so this pin is almost certainly unnecessary.
                // Confirm no BigDecimal reaches a response body or an archived JSON file, then remove it.
                .enable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }
}
