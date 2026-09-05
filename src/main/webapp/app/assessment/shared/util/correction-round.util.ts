/**
 * Every server endpoint that takes a `correction-round` binds it to a Java `int`, so a larger value is not a round
 * either: it reaches the endpoint unchanged and comes back as a 400 rather than opening the first round.
 */
const MAX_CORRECTION_ROUND = 2 ** 31 - 1;

/**
 * The correction round an assessment page works on is carried only in the `correction-round` query parameter. It is both
 * sent to the server as the round to load and used to index the results of the loaded submission, so it has to be one
 * value derived in one place. `Number()` cannot be used for that on its own: it turns `null`, `''` and whitespace all
 * into `0`, and it happily produces `NaN`, `Infinity` or a fraction from a hand-edited URL. Such a value then reaches
 * the endpoint as the requested round and the results array as an index (issue #13396).
 *
 * An absent or unusable parameter means the first correction round, which is what a request without the parameter has
 * always meant. The URL is the only authority: falling back to the round a page happens to be on instead would make the
 * same URL show different rounds depending on how it was reached.
 *
 * @param rawCorrectionRound the raw query parameter value, as returned by `ParamMap#get`
 * @returns the correction round named by the parameter, or the first one when it is absent or unusable
 */
export function parseCorrectionRound(rawCorrectionRound: string | null | undefined): number {
    const normalizedCorrectionRound = rawCorrectionRound?.trim();
    // Digits only. Artemis always writes the round as a plain decimal integer, and `Number()` accepts far more than
    // that: `1e3` becomes 1000, `0x2` becomes 2 and `+1` becomes 1, none of which any link produces. Accepting them
    // would only widen what a hand-edited URL can push into the request and into the results index.
    if (!normalizedCorrectionRound || !/^\d+$/.test(normalizedCorrectionRound)) {
        return 0;
    }
    // The regular expression leaves only non-negative decimal integers, so the sole remaining way to be unusable is
    // being too large for the `int` the server binds this to. That also covers a digit string long enough to lose
    // precision, or to become `Infinity`, since both are far beyond that bound.
    const correctionRound = Number(normalizedCorrectionRound);
    return correctionRound <= MAX_CORRECTION_ROUND ? correctionRound : 0;
}
