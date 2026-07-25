/**
 * Mirrors the server IrisCommandAckDTO record.
 * The client's reply to a server-pushed command (see {@link IrisPointOut}): whether it was carried out on the client.
 */
export class IrisCommandAckDTO {
    correlationId!: string;
    applied!: boolean;
}
