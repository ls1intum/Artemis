// SPDX-License-Identifier: Apache-2.0
// Adapted from the CodeAbility Sharing Plugin Platform API. See META-INF/NOTICE.
package de.tum.cit.aet.artemis.sharing.connector;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Exercises selected for transfer by a user.
 *
 * @param userInfo        information about the user checking out the basket
 * @param exerciseInfo    selected exercises, in basket position order
 * @param tokenValidUntil token expiry in milliseconds since the Unix epoch
 * @author Michael Breu
 */
public record ShoppingBasket(UserInfo userInfo, List<SearchResult> exerciseInfo, long tokenValidUntil) {

    public ShoppingBasket {
        exerciseInfo = exerciseInfo == null ? null : exerciseInfo.stream().toList();
    }

    /**
     * User who checked out the basket.
     *
     * @param email identifier supplied by the platform
     * @author Michael Breu
     */
    public record UserInfo(String email) {
    }

    /** An exercise hit with its repository, metadata and applicable actions. */
    @JsonInclude
    public record SearchResult(Project project, MetadataFile file, ExerciseMetadata metadata, SearchStatistics searchStatistics, String exerciseId, int ranking5,
            List<PluginActionInfo> supportedActions) {

        public SearchResult {
            supportedActions = supportedActions == null ? null : supportedActions.stream().toList();
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        private SearchResult(JsonInput input) {
            this(input.project, input.file, input.metadata, input.searchStatistics, input.exerciseId, input.ranking5, input.supportedActions);
        }

        // Omission yields an empty list. Explicit null still represents a null list.
        private static final class JsonInput {

            public Project project;

            public MetadataFile file;

            public ExerciseMetadata metadata;

            public SearchStatistics searchStatistics;

            public String exerciseId;

            public int ranking5;

            public List<PluginActionInfo> supportedActions = List.of();
        }

        /** An action the user can perform on a search result. */
        @JsonInclude
        public record PluginActionInfo(String plugin, String action, String commandName) {
        }

        /** Repository file associated with an indexed exercise. */
        @JsonInclude
        public record MetadataFile(String filename, String path, @JsonProperty("commit_id") String commitId, String parentId, List<String> children,
                @JsonIgnore Instant indexingDate, @JsonIgnore Instant lastActivityAt) {

            public MetadataFile {
                children = children == null ? null : children.stream().toList();
            }

            @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
            private MetadataFile(@JsonProperty("filename") String filename, @JsonProperty("path") String path, @JsonProperty("commit_id") String commitId,
                    @JsonProperty("parentId") String parentId, @JsonProperty("children") List<String> children, @JsonProperty("indexing_date") Object indexingDate,
                    @JsonProperty("last_activity_at") Object lastActivityAt) {
                this(filename, path, commitId, parentId, children, WireTimestamp.parse(indexingDate), WireTimestamp.parse(lastActivityAt));
            }

            @JsonGetter("indexing_date")
            private String indexingDateOnWire() {
                return WireTimestamp.format(indexingDate);
            }

            @JsonGetter("last_activity_at")
            private String lastActivityOnWire() {
                return WireTimestamp.format(lastActivityAt);
            }
        }
    }

    /** Repository metadata returned by the Sharing Platform. */
    @JsonInclude
    @JsonIgnoreProperties("projectId")
    public record Project(@JsonProperty("project_id") int id, @JsonProperty("project_name") String projectName, String namespace, @JsonProperty("main_group") String mainGroup,
            @JsonProperty("sub_group") String subGroup, String url, @JsonIgnore Instant lastActivityAt, ProjectVisibility visibility, List<String> users, List<String> groups,
            @JsonProperty("star_count") int starCount, @JsonProperty("open_issues_count") int openIssuesCount, @JsonProperty("forks_count") int forksCount, String description,
            boolean archived, @JsonProperty("commit_id") String commitId) {

        public Project {
            users = users == null ? null : users.stream().toList();
            groups = groups == null ? null : groups.stream().toList();
        }

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        private Project(@JsonProperty("project_id") int id, @JsonProperty("project_name") String projectName, @JsonProperty("namespace") String namespace,
                @JsonProperty("main_group") String mainGroup, @JsonProperty("sub_group") String subGroup, @JsonProperty("url") String url,
                @JsonProperty("last_activity_at") Object lastActivityAt, @JsonProperty("visibility") ProjectVisibility visibility, @JsonProperty("users") List<String> users,
                @JsonProperty("groups") List<String> groups, @JsonProperty("star_count") int starCount, @JsonProperty("open_issues_count") int openIssuesCount,
                @JsonProperty("forks_count") int forksCount, @JsonProperty("description") String description, @JsonProperty("archived") boolean archived,
                @JsonProperty("commit_id") String commitId) {
            this(id, projectName, namespace, mainGroup, subGroup, url, WireTimestamp.parse(lastActivityAt), visibility, users, groups, starCount, openIssuesCount, forksCount,
                    description, archived, commitId);
        }

        @JsonGetter("last_activity_at")
        private String lastActivityOnWire() {
            return WireTimestamp.format(lastActivityAt);
        }
    }

    /** Visibility of a project returned by the Sharing Platform. */
    public enum ProjectVisibility {

        /** Accessible publicly. */
        PUBLIC("public"),
        /** Access is restricted to authorized users. */
        PRIVATE("private"),
        /** Access is restricted to the platform's internal users. */
        INTERNAL("internal");

        private final String wireValue;

        ProjectVisibility(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Resolves a case-sensitive platform value. Unrecognized values are treated as missing.
         *
         * @param value the platform value
         * @return the matching visibility or {@code null}
         */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ProjectVisibility fromWireValue(String value) {
            for (ProjectVisibility visibility : values()) {
                if (visibility.wireValue.equals(value)) {
                    return visibility;
                }
            }
            return null;
        }

        /**
         * @return the value sent on the wire
         */
        @JsonValue
        public String toWireValue() {
            return wireValue;
        }
    }

    /** Counters and badge status for a search result. */
    @JsonInclude
    public record SearchStatistics(Integer views, Integer downloads, Boolean badgeRewarded) {

    }

    /** User-provided exercise metadata returned by the platform. */
    @JsonInclude
    @JsonIgnoreProperties("collectionContent")
    public record ExerciseMetadata(List<String> assesses, String audience, List<Person> contributor, List<Person> creator, String description, String difficulty,
            String educationalAlignment, String educationalFramework, List<String> educationalLevel, List<String> educationalUse, List<String> format, String identifier,
            String interactivityType, List<String> isBasedOn, String image, List<String> keyword, List<String> language, String learningResourceType, String license,
            String metadataVersion, List<String> programmingLanguage, List<Person> publisher, List<String> requires, String status, String structure, List<String> subject,
            List<String> teaches, String timeRequired, String title, String typicalAgeRange, String version, Validity valid, PublicVisibility publicVisibility,
            String repositoryURL) {

        public ExerciseMetadata {
            assesses = assesses == null ? null : assesses.stream().toList();
            contributor = contributor == null ? null : contributor.stream().toList();
            creator = creator == null ? null : creator.stream().toList();
            educationalLevel = educationalLevel == null ? null : educationalLevel.stream().toList();
            educationalUse = educationalUse == null ? null : educationalUse.stream().toList();
            format = format == null ? null : format.stream().toList();
            isBasedOn = isBasedOn == null ? null : isBasedOn.stream().toList();
            keyword = keyword == null ? null : keyword.stream().toList();
            language = language == null ? null : language.stream().toList();
            programmingLanguage = programmingLanguage == null ? null : programmingLanguage.stream().toList();
            publisher = publisher == null ? null : publisher.stream().toList();
            requires = requires == null ? null : requires.stream().toList();
            subject = subject == null ? null : subject.stream().toList();
            teaches = teaches == null ? null : teaches.stream().toList();
        }

        /** Date range for which the metadata is valid. */
        public record Validity(Date start, Date end) {

            public Validity {
                start = copy(start);
                end = copy(end);
            }

            @Override
            public Date start() {
                return copy(start);
            }

            @Override
            public Date end() {
                return copy(end);
            }

            private static Date copy(Date date) {
                return date == null ? null : new Date(date.getTime());
            }
        }

        /** Restrictions on the public visibility of exercise metadata. */
        public record PublicVisibility(List<String> except) {

            public PublicVisibility {
                except = except == null ? null : except.stream().toList();
            }
        }
    }

    /** A person associated with an exercise. */
    public record Person(String name, String affiliation, String email) {

    }

    /** Converts the Sharing Platform's JSON timestamp forms at the DTO boundary. */
    private static final class WireTimestamp {

        private WireTimestamp() {
        }

        static String format(Instant value) {
            return value == null ? null : value.toString();
        }

        static Instant parse(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String text) {
                String timestamp = text.trim();
                if (timestamp.isEmpty()) {
                    return null;
                }
                if (timestamp.endsWith("Z")) {
                    timestamp = timestamp.substring(0, timestamp.length() - 1);
                }
                return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
            }
            if (value instanceof List<?> parts) {
                if (parts.isEmpty()) {
                    return null;
                }
                if (parts.size() >= 5 && parts.size() <= 7) {
                    int second = parts.size() >= 6 ? component(parts, 5) : 0;
                    int nano = parts.size() == 7 ? component(parts, 6) : 0;
                    return LocalDateTime.of(component(parts, 0), component(parts, 1), component(parts, 2), component(parts, 3), component(parts, 4), second, nano)
                            .toInstant(ZoneOffset.UTC);
                }
            }
            throw new IllegalArgumentException("Expected an ISO-8601 timestamp or a date-time array");
        }

        private static int component(List<?> parts, int index) {
            if (parts.get(index) instanceof Integer number) {
                return number;
            }
            throw new IllegalArgumentException("Expected an integer date-time component");
        }
    }
}
