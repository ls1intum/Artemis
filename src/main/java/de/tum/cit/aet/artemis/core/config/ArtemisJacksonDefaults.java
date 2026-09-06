package de.tum.cit.aet.artemis.core.config;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.cfg.MapperBuilder;

/**
 * The single definition of how Artemis configures a Jackson mapper.
 * <p>
 * Applied to the auto-configured {@code JsonMapper} by {@link JacksonConfiguration} and to the shared static mapper in
 * {@code JsonObjectMapper}, so that a value serialized outside a Spring bean cannot come out different from the same
 * value serialized by a controller.
 * <p>
 * Everything here restores a Jackson 2 default that Jackson 3 changed and that would otherwise be visible on the wire.
 * The Jackson 3 defaults Artemis deliberately keeps are {@code SORT_PROPERTIES_ALPHABETICALLY} (deterministic
 * responses and course archives), {@code FAIL_ON_TRAILING_TOKENS} (stricter parsing) and {@code FAIL_ON_EMPTY_BEANS}
 * (an unresolvable Hibernate proxy serializes as <code>{}</code> instead of throwing).
 * <p>
 * Every pin is meant to be temporary, and each carries a TODO naming what has to happen before it can go — dropping
 * one is an API change that needs the client migrated in the same release, not a config tweak.
 * {@code JacksonSerializationContractTest} is what tells you whether a pin still matters: remove it, run the test, and the
 * fixture that fails is the payload the change would alter.
 */
public final class ArtemisJacksonDefaults {

    private ArtemisJacksonDefaults() {
    }

    /**
     * Applies the Artemis defaults to any Jackson 3 mapper builder.
     *
     * @param builder the builder to configure, for JSON or for XML
     * @param <M>     the mapper type the builder produces
     * @param <B>     the builder type, returned so calls can be chained
     * @return the same builder
     */
    public static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B apply(B builder) {
        return builder
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
                // JacksonSerializationContractTest covers both.
                // Jackson 3 stops stripping trailing zeroes, so a BigDecimal read into a JsonNode renders differently.
                // TODO: only one production file imports BigDecimal, so this pin is almost certainly unnecessary.
                // Confirm no BigDecimal reaches a response body or an archived JSON file, then remove it.
                .enable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }
}
