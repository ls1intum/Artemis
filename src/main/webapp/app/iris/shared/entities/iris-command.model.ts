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
    /**
     * The browser tab that answers for this command — the one the chat run was started from. Delivery is per user, so
     * every tab with the session open receives the command and carries it out; all but this one stay silent about the
     * outcome. Absent means no tab was named and any of them may answer.
     */
    targetClientId?: string;
}

/**
 * Mirrors the server IrisCommandAckDTO record.
 * The client's reply to a server-pushed {@link IrisCommand}: whether it was carried out on the client.
 */
export interface IrisCommandAckDTO {
    /** Correlates the reply with the command that asked for it. */
    correlationId: string;
    /** Whether the client carried the command out. */
    applied: boolean;
}
