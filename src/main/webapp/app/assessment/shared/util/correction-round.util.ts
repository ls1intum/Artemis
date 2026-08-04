/**
 * The correction round an assessment page works on is carried only in the `correction-round` query parameter, so it is
 * read in several places: the assessment editors and the route resolvers that load the participation before an editor
 * even exists. They have to agree, otherwise a page can load the data of one round while believing it is in another.
 *
 * `Number()` alone cannot be used for that: it turns `null`, `''` and whitespace all into `0`, which is
 * indistinguishable from an explicit `correction-round=0`, and it happily produces `NaN`, `Infinity` or a fraction from
 * a hand-edited URL. A missing round silently meaning the first one is what made a second correction round collapse
 * into the first one in issue #13396.
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
 * The correction round to request data for, for callers that need a concrete round rather than "unknown". An absent or
 * unusable parameter falls back to the first correction round, which is what a request without the parameter has always
 * meant, and which keeps an unusable value from reaching the server.
 *
 * @param rawCorrectionRound the raw query parameter value, as returned by `ParamMap#get`
 * @returns the correction round to load, defaulting to the first one
 */
export function correctionRoundToLoad(rawCorrectionRound: string | null | undefined): number {
    return parseCorrectionRound(rawCorrectionRound) ?? 0;
}
