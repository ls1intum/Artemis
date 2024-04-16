package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.*;
import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static de.tum.in.www1.artemis.versioning.VersionRangeService.versionRangeToIntegerList;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import de.tum.in.www1.artemis.exception.ApiVersionRangeNotValidException;

/**
 * A request condition for {@link VersionRanges} controlling a set of {@link VersionRange}s.
 */
public class VersionRangesRequestCondition implements RequestCondition<VersionRangesRequestCondition> {

    // Expect optional leading slash, "api/", optional numerical version with a "v" prefix and some path afterward
    private static final Pattern PATH_PATTERN = Pattern.compile("^/?api(?:/v(\\d+))?/.+$");

    private static final Logger log = LoggerFactory.getLogger(VersionRangesRequestCondition.class);

    private final Set<VersionRange> ranges;

    private final List<Integer> apiVersions;

    // Comparison codes that specify whether two ranges collide or not
    private final Set<VersionRangeComparisonType> inRangeCodes = Set.of(A_CUT_B, B_CUT_A, A_INCLUDES_B, B_INCLUDES_A, EQUALS);

    public VersionRangesRequestCondition(@NotNull List<Integer> apiVersions, @NotNull VersionRange... ranges) {
        this(apiVersions, Arrays.asList(ranges));
    }

    /**
     * Creates a new instance with the given version ranges.
     *
     * @param ranges the version ranges to use
     */
    public VersionRangesRequestCondition(@NotNull List<Integer> apiVersions, @NotNull Collection<VersionRange> ranges) {
        this.apiVersions = apiVersions;
        var distinct = ranges.stream().distinct().toList();
        if (distinct.size() != 1 || distinct.getFirst() != null) {
            checkRangesValidity(distinct);
            this.ranges = Set.copyOf(distinct);
        }
        else {
            this.ranges = Collections.emptySet();
        }
    }

    private void checkRangesValidity(Collection<VersionRange> ranges) {
        if (ranges.isEmpty()) {
            return;
        }
        ranges.forEach(range -> {
            int length = range.value().length;
            if (length != 1 && length != 2) {
                throw new ApiVersionRangeNotValidException();
            }
        });
    }

    /**
     * Returns all valid version numbers from the given version ranges.
     * If ranges are empty, all versions are valid.
     *
     * @return A list of valid version numbers
     */
    public List<Integer> getApplicableVersions() {
        if (ranges.isEmpty()) {
            return apiVersions;
        }
        // Collect all versions that collide with at least one range
        return apiVersions.stream().filter(this::containsVersion).toList();
    }

    private boolean containsVersion(int version) {
        return ranges.stream().anyMatch(range -> inRangeCodes.contains(VersionRangeComparator.compare(range, getInstanceOfVersionRange(version, version))));
    }

    /**
     * Combines two {@link VersionRangesRequestCondition}s into one.
     *
     * @param other the condition to combine with.
     * @return the combined condition.
     * @throws ApiVersionRangeNotValidException If the value of a {@link VersionRange} is not valid
     */
    @Override
    public @NotNull VersionRangesRequestCondition combine(@NotNull VersionRangesRequestCondition other) {
        if (ranges.isEmpty() || other.ranges.isEmpty()) {
            return new VersionRangesRequestCondition(apiVersions);
        }

        // Separate ranges from start limits
        Set<VersionRange> combinedRanges = new HashSet<>(other.ranges);
        combinedRanges.addAll(this.ranges);

        Set<VersionRange> limits = new HashSet<>();
        Set<VersionRange> ranges = new HashSet<>();

        combinedRanges.forEach(range -> {
            if (range.value().length == 1) {
                limits.add(range);
            }
            else if (range.value().length == 2) {
                ranges.add(range);
            }
            else {
                // Fallback. Exception would've been already thrown earlier in the constructor
                throw new ApiVersionRangeNotValidException();
            }
        });

        // Combine limits
        VersionRange resultLimit = limits.stream().reduce(VersionRangeFactory::combine).orElse(null);

        // Return new condition of limit if no ranges exist
        if (resultLimit != null && ranges.isEmpty()) {
            return new VersionRangesRequestCondition(apiVersions, getInstanceOfVersionRange(resultLimit.value()[0]));
        }

        // Combine limit with ranges
        return combineLimitAndRanges(resultLimit, ranges);
    }

    /**
     * Combines a limit with a list of ranges to a new condition.
     *
     * @param limit  The limit to combine with.
     * @param ranges The ranges to combine with.
     * @return The combined condition.
     */
    private VersionRangesRequestCondition combineLimitAndRanges(VersionRange limit, Set<VersionRange> ranges) {
        Integer limitStart = null;
        var rangePool = new ArrayList<>(ranges);
        List<VersionRange> newRanges = new ArrayList<>();

        // If limit exists, ignore ranges within the limit, otherwise add all ranges to the pool
        if (limit != null) {
            limitStart = limit.value()[0];
            // Select ranges outside the limit
            while (!rangePool.isEmpty()) {
                var range = rangePool.removeFirst();
                if (range.value()[0] >= limitStart) {
                    // Already part of limit
                    continue;
                }
                if (range.value()[1] < limitStart - 1) {
                    // range is completely before limit
                    newRanges.add(range);
                }
                else {
                    limitStart = range.value()[0];
                }
            }
        }
        else {
            newRanges.addAll(rangePool);
        }

        var simplifyRanges = simplifyRanges(newRanges);

        // Build new condition and return it
        List<VersionRange> annotationList = simplifyRanges.stream().map(range -> getInstanceOfVersionRange(range.value()[0], range.value()[1]))
                .collect(Collectors.toCollection(ArrayList::new));
        if (limitStart != null) {
            annotationList.add(getInstanceOfVersionRange(limitStart));
        }
        return new VersionRangesRequestCondition(apiVersions, annotationList);
    }

    /**
     * Simplifies a list of ranges by combining overlapping ranges.
     *
     * @param rangePool The list of ranges to simplify.
     * @return The simplified list of ranges.
     */
    private List<VersionRange> simplifyRanges(List<VersionRange> rangePool) {
        List<VersionRange> newRanges = new ArrayList<>();
        // As long as there are ranges in the pool, pop the first one and check against all other ranges
        // If there is an overlap, remove the second range, combine the two ranges, and add the new range to the pool
        while (!rangePool.isEmpty()) {
            var selectedRange = rangePool.removeFirst();
            boolean combined = false;
            for (VersionRange range : rangePool) {
                boolean canCombine = switch (VersionRangeComparator.compare(selectedRange, range)) {
                    case FIRST_A_NO_INTERSECT, FIRST_B_NO_INTERSECT -> false;
                    default -> true;
                };
                if (canCombine) {
                    rangePool.remove(range);
                    rangePool.add(VersionRangeFactory.combine(selectedRange, range));
                    combined = true;
                    break;
                }
            }
            if (!combined) {
                newRanges.add(selectedRange);
            }
        }
        return newRanges;
    }

    /**
     * Checks if the given condition collides with this condition.
     *
     * @param versionRangesRequestCondition The condition to check.
     * @return True if the conditions collide, false otherwise.
     */
    public boolean collide(@NotNull VersionRangesRequestCondition versionRangesRequestCondition) {
        Set<VersionRange> tbcRanges = versionRangesRequestCondition.getRanges();

        // If one of those represents all versions, the other one has to collide
        if (ranges.isEmpty() || tbcRanges.isEmpty()) {
            return true;
        }

        for (VersionRange range : ranges) {
            for (VersionRange tbcRange : tbcRanges) {
                if (inRangeCodes.contains(VersionRangeComparator.compare(range, tbcRange))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<VersionRange> getRanges() {
        return ranges;
    }

    @Override
    public int compareTo(@NotNull VersionRangesRequestCondition versionRangesRequestCondition, @NotNull HttpServletRequest httpServletRequest) {
        if (this.getMatchingCondition(httpServletRequest) == this) {
            return -1;
        }
        else if (versionRangesRequestCondition.getMatchingCondition(httpServletRequest) == versionRangesRequestCondition) {
            return 1;
        }
        else {
            return 0;
        }
    }

    /**
     * Returns the condition that matches the request or {@code null} if there is no match.
     *
     * @param request the current request
     * @return the matching condition or {@code null}
     */
    @Nullable
    @Override
    public VersionRangesRequestCondition getMatchingCondition(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") && !path.startsWith("api/")) {
            return null;
        }
        // If any version is valid, return this
        if (ranges.isEmpty()) {
            return this;
        }

        for (VersionRange range : ranges) {
            if (versionRangeMatchesRequest(range, request)) {
                return this;
            }
        }

        return null;
    }

    /**
     * Checks if the range matches the request.
     *
     * @param range   the version range to match
     * @param request the current request
     * @return true if the range matches the request, false otherwise
     */
    public boolean versionRangeMatchesRequest(@NotNull VersionRange range, @NotNull HttpServletRequest request) {
        String path = request.getRequestURI();
        var matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return false;
        }
        if (matcher.groupCount() == 1) {
            if (matcher.group(1) == null) {
                // No version found, assume the latest version
                int latestVersion = apiVersions.getLast();
                return checkVersion(range, latestVersion);
            }
            else {
                int requestedVersion = Integer.parseInt(matcher.group(1));
                return checkVersion(range, requestedVersion);
            }
        }

        log.error("Request for path {} matches request pattern but has illegal pattern group count {}.", path, matcher.groupCount());
        return false;
    }

    /**
     * Check if the requested version is part of the version range
     *
     * @param requestedVersion the requested version
     * @return true if the requested version is part of the version range
     */
    private boolean checkVersion(@NotNull VersionRange range, int requestedVersion) {
        List<Integer> versions = versionRangeToIntegerList(range);
        // only allowed versions here
        if (apiVersions.contains(requestedVersion)) {
            int startVersion = versions.get(0);

            return requestedVersion >= startVersion && (versions.size() == 1 || requestedVersion <= versions.get(1));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRanges(), inRangeCodes);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof VersionRangesRequestCondition that)) {
            return false;
        }

        var thisRanges = ranges.stream().map(range -> Arrays.stream(range.value()).boxed().toList()).collect(Collectors.toSet());
        var otherRanges = that.ranges.stream().map(range -> Arrays.stream(range.value()).boxed().toList()).collect(Collectors.toSet());

        return thisRanges.equals(otherRanges);
    }
}
