package de.tum.cit.aet.artemis.calendar.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.AbstractAssert;

public class TimestampFormatAssert extends AbstractAssert<TimestampFormatAssert, String> {

    private static final String TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    private static final String ZONE_SUFFIX_REGEX = "([+-]\\d{2}:\\d{2}|Z)";

    private static final String FULL_ZONED_REGEX = TIMESTAMP_REGEX + ZONE_SUFFIX_REGEX;

    private static final String FULL_UTC_REGEX = TIMESTAMP_REGEX + "Z";

    protected TimestampFormatAssert(String actual) {
        super(actual, TimestampFormatAssert.class);
    }

    public static TimestampFormatAssert assertThat(String actual) {
        return new TimestampFormatAssert(actual);
    }

    public void hasIso8601OffsetTimestamps(String... fieldNames) {
        isNotNull();
        for (String field : fieldNames) {
            validateFieldMatchesFormat(field, FULL_ZONED_REGEX, "zoned timestamp (ISO 8601 with offset or Z)");
        }
    }

    public void hasIso8601UtcTimestamps(String... fieldNames) {
        isNotNull();
        for (String field : fieldNames) {
            validateFieldMatchesFormat(field, FULL_UTC_REGEX, "UTC timestamp (ending in 'Z')");
        }
    }

    private void validateFieldMatchesFormat(String fieldName, String expectedFormatRegex, String formatDescription) {
        Pattern fieldPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = fieldPattern.matcher(actual);

        int totalOccurrences = 0;
        int validOccurrences = 0;

        while (matcher.find()) {
            totalOccurrences++;
            String timestamp = matcher.group(1);
            if (timestamp.matches(expectedFormatRegex)) {
                validOccurrences++;
            }
        }

        if (totalOccurrences == 0) {
            failWithMessage("Expected field '%s' to appear in the response, but it was not found.", fieldName);
        }
        else if (validOccurrences != totalOccurrences) {
            failWithMessage("Field '%s' had %d occurrences, but only %d matched the expected %s format.", fieldName, totalOccurrences, validOccurrences, formatDescription);
        }
    }
}
