/**
 * Mirrors the server IrisCommandAckDTO record.
 * The client's reply to an {@link IrisCommandRequestDTO}: whether the command was carried out on the client.
 */
export class IrisCommandAckDTO {
    correlationId!: string;
    applied!: boolean;
}
