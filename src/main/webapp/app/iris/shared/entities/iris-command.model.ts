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
 * Mirrors the server IrisCommandAckDTO record.
 * The client's reply to a server-pushed {@link IrisCommand}: whether it was carried out on the client.
 */
export class IrisCommandAckDTO {
    correlationId!: string;
    applied!: boolean;
}
