/**
 * The correction round an assessment page works on is carried only in the `correction-round` query parameter, and it is
 * both sent to the server as the round to load and used to index the results of the loaded submission. `Number()` alone
 * cannot be trusted with that: it turns `null`, `''` and whitespace all into `0`, which is indistinguishable from an
 * explicit `correction-round=0`, and it happily produces `NaN`, `Infinity` or a fraction from a hand-edited URL. Such a
 * value then reaches the endpoint as the requested round and the results array as an index (issue #13396).
 *
 * Whoever both loads and displays the data reads the parameter with this function and keeps its current round when the
 * parameter is unusable, because that round is the one it requested. Where loading and displaying are split, as in the
 * text assessment whose route resolver loads before the component exists, the round must not be derived twice. There the
 * resolver uses {@link correctionRoundToLoad} and hands the round it requested to the page.
 *
 * @param rawCorrectionRound the raw query parameter value, as returned by `ParamMap#get`
 * @returns the correction round, or undefined when the parameter is absent or not a usable round
 */
export function parseCorrectionRound(rawCorrectionRound: string | null | undefined): number | undefined {
    const normalizedCorrectionRound = rawCorrectionRound?.trim();
    if (!normalizedCorrectionRound) {
        return undefined;
    }
    const correctionRound = Number(normalizedCorrectionRound);
    return Number.isSafeInteger(correctionRound) && correctionRound >= 0 ? correctionRound : undefined;
}

/**
 * The correction round to request data for, for callers that have to name a concrete round rather than "unknown". A route
 * resolver is such a caller: it runs per navigation and has no round of its own to keep, so an absent or unusable
 * parameter falls back to the first correction round, which is what a request without the parameter has always meant.
 *
 * The round returned here is the one the loaded data belongs to, so it is handed on to the page instead of being derived
 * from the URL again. Two independent fallbacks would let the page index a round the request never asked for.
 *
 * @param rawCorrectionRound the raw query parameter value, as returned by `ParamMap#get`
 * @returns the correction round to load, defaulting to the first one
 */
export function correctionRoundToLoad(rawCorrectionRound: string | null | undefined): number {
    return parseCorrectionRound(rawCorrectionRound) ?? 0;
}
