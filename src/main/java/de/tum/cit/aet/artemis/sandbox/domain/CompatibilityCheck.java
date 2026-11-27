package de.tum.cit.aet.artemis.sandbox.domain;

import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The result of checking a policy against an Ares release.
 *
 * @param verdict                  the result
 * @param unsupportedFields        the field paths the release does not accept, in the order of the file, each once; only the outermost unknown field of a subtree is listed
 * @param unsupportedFormatVersion the format version of the policy if the release does not accept it
 */
public record CompatibilityCheck(CompatibilityVerdict verdict, List<String> unsupportedFields, @Nullable BigInteger unsupportedFormatVersion) {

    /** The result for a policy the release accepts. */
    public static final CompatibilityCheck COMPATIBLE = new CompatibilityCheck(CompatibilityVerdict.COMPATIBLE, List.of(), null);

    /** The result if the release or the policy cannot be checked. */
    public static final CompatibilityCheck UNVERIFIED = new CompatibilityCheck(CompatibilityVerdict.UNVERIFIED, List.of(), null);
}
