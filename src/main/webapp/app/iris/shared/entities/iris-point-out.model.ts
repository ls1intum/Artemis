/**
 * A command Iris asks the client to carry out, in the shape it travels in everywhere: over the WebSocket
 * while the pipeline waits (mirrors the server IrisCommandRequestWebsocketDTO) and, once applied, as the
 * JSON content of a COMMAND marker in the chat history.
 *
 * The type is open on purpose, but only known types are acted on: the client switches on it and acknowledges
 * anything without a case as not applied, so a new command type can ship without breaking older clients.
 */
export interface IrisCommand {
    /** Command type discriminator. */
    type: string;
    /** Set when the Iris pipeline is waiting on this command; the ack must carry the same id. */
    correlationId?: string;
    /** Command-specific fields forwarded from Pyris. */
    parameters?: Record<string, unknown>;
}

/**
 * A point-out resolved into the navigation the combined view performs. An {@link IrisCommand} of type
 * "pointOut" is validated and converted into this shape once, so the position is read from typed fields
 * instead of the untyped parameter bag.
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
