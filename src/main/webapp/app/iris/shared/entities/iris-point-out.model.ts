/**
 * A point-out resolved into the navigation the combined view performs. A command of type "pointOut" is
 * validated and converted into this shape once, so the position is read from typed fields instead of the
 * untyped parameter bag.
 *
 * The same shape covers the two point-outs that arrive without a command: one read back from a COMMAND
 * marker in the history (then {@link lectureUnitName} is set) and one raised by clicking such a marker
 * (then {@link forceOpen} is set).
 */
export interface IrisPointOut {
    lectureUnitId: number;
    /** Slide page to display, counted from the start of the deck. */
    page?: number;
    /** Video position in seconds to seek to. */
    timestamp?: number;
    /** Set when a pipeline is waiting on this point-out; the ack must carry the same id. */
    correlationId?: string;
    /** True for a marker click: (re)open the combined view if the student closed it. */
    forceOpen?: boolean;
    /** Display name of the lecture unit, stored on history markers so they can be labelled. */
    lectureUnitName?: string;
}

/**
 * Reads a point-out from the parameters of a command, which must name a lecture unit and at least one of
 * page / timestamp. Commands pushed by the server and COMMAND markers read back from the chat history carry
 * the same parameters, so both are parsed here; the caller establishes that the command type is "pointOut".
 * @param parameters the command's parameters
 * @returns the point-out if the parameters hold up, undefined otherwise
 */
export function parsePointOut(parameters: Record<string, unknown> | undefined): IrisPointOut | undefined {
    if (typeof parameters?.['lectureUnitId'] !== 'number') {
        return undefined;
    }
    const page = typeof parameters['page'] === 'number' ? parameters['page'] : undefined;
    const timestamp = typeof parameters['timestamp'] === 'number' ? parameters['timestamp'] : undefined;
    if (page === undefined && timestamp === undefined) {
        return undefined;
    }
    // Only markers carry the unit name; a server-pushed command simply leaves it undefined.
    const lectureUnitName = typeof parameters['lectureUnitName'] === 'string' ? parameters['lectureUnitName'] : undefined;
    return { lectureUnitId: parameters['lectureUnitId'], page, timestamp, lectureUnitName };
}

/**
 * Reads the point-out recorded on a COMMAND marker, which stores the executed command in the same
 * {type, parameters} shape it was sent in. The caller establishes the marker's command type.
 * @param marker the marker's JSON attributes
 * @returns the point-out if the marker holds up, undefined otherwise
 */
export function getPointOut(marker: Record<string, unknown>): IrisPointOut | undefined {
    return parsePointOut(marker['parameters'] as Record<string, unknown> | undefined);
}
