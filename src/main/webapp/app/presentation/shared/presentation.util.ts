/** Whether a value is present, so neither undefined nor null. */
export function isSet(value: unknown): boolean {
    return value !== undefined && value !== null;
}

/** The level positions of a rubric scale with the given number of levels, highest first, as a rating scale is read from best to worst. */
export function levelPositionsHighestFirst(levelCount: number): number[] {
    return Array.from({ length: levelCount }, (_, position) => levelCount - 1 - position);
}
