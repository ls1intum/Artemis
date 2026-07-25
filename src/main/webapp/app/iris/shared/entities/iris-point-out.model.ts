/**
 * A request to point the student to a position in the lecture combined view.
 *
 * The same shape covers the three places a point-out appears:
 * - pushed by the server over WebSocket while the Iris pipeline waits (mirrors the server
 *   IrisCommandRequestWebsocketDTO) — then {@link correlationId} is set and the outcome must be acknowledged;
 * - stored as the JSON content of a COMMAND marker in the chat history — then {@link lectureUnitName} is set;
 * - raised locally when the student clicks such a marker — then {@link forceOpen} is set.
 */
export interface IrisPointOut {
    /** Command type discriminator; the server sets "pointOut". */
    type: string;
    lectureUnitId: number;
    /** 1-based slide page to display. */
    page?: number;
    /** Video position in seconds to seek to. */
    timestamp?: number;
    /** Set when the Iris pipeline is waiting on this point-out; the ack must carry the same id. */
    correlationId?: string;
    /** True for a marker click: (re)open the combined view if the student closed it. */
    forceOpen?: boolean;
    /** Display name of the lecture unit, stored on history markers so they can be labelled. */
    lectureUnitName?: string;
}
