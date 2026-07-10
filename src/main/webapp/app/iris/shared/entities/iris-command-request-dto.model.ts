/**
 * Mirrors the server IrisCommandRequestWebsocketDTO record.
 * A request, sent while the Iris pipeline is still running, for the client to carry out a command
 * (currently only a point-out: navigate the combined view if it is still open) and acknowledge the outcome.
 */
export class IrisCommandRequestDTO {
    correlationId!: string;
    /** Command type discriminator (currently only "pointOut"). */
    type!: string;
    lectureUnitId!: number;
    page?: number;
    timestamp?: number;
}
