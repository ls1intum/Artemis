package de.tum.cit.aet.artemis.localvc.service;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches a name against a regular expression that was written by an instructor, within a time budget. The expression is evaluated by backtracking, so the time it needs can grow
 * very quickly with the length of the name. The name is wrapped in a character sequence that checks the time on every access of a character, so that a match that takes too long
 * ends and counts as a name that does not match.
 * <p>
 * The deadline is checked when a character is read. It is a second layer behind {@link BranchRegexSyntax}, which only supports expressions whose matching reads a character in
 * every repetition, so the deadline applies to every expression that is matched here.
 */
public final class TimeBoundedRegexMatcher {

    private static final Logger log = LoggerFactory.getLogger(TimeBoundedRegexMatcher.class);

    /** The time that a single match may take. */
    static final long MAX_MATCH_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private TimeBoundedRegexMatcher() {
        // utility class
    }

    /**
     * Whether the whole input matches the pattern, within the default time budget.
     *
     * @param pattern the compiled expression
     * @param input   the name to match
     * @return true if the input matches in time, false if it does not match or the time budget was used up
     */
    public static boolean matches(Pattern pattern, CharSequence input) {
        return matches(pattern, input, MAX_MATCH_NANOS);
    }

    static boolean matches(Pattern pattern, CharSequence input, long budgetNanos) {
        try {
            return pattern.matcher(new DeadlineCharSequence(input, System.nanoTime() + budgetNanos)).matches();
        }
        catch (MatchTimeoutException e) {
            log.warn("Matching a name against the expression '{}' took longer than {} ms and is treated as no match", pattern.pattern(),
                    TimeUnit.NANOSECONDS.toMillis(budgetNanos));
            return false;
        }
    }

    private static final class MatchTimeoutException extends RuntimeException {

        MatchTimeoutException() {
            // no stack trace is needed, the exception only ends the match
            super("The time budget of the match is used up", null, false, false);
        }
    }

    private static final class DeadlineCharSequence implements CharSequence {

        private final CharSequence delegate;

        private final long deadlineNanos;

        DeadlineCharSequence(CharSequence delegate, long deadlineNanos) {
            this.delegate = delegate;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new MatchTimeoutException();
            }
            return delegate.charAt(index);
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
