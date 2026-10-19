import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';
import { IrisMessageContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';

/**
 * Pending context change forwarded with a new user message so the server can apply the switch
 * atomically (CTXSWAP marker, then user message) in one round trip. `mode` is a {@link ChatServiceMode} value.
 */
export interface IrisPendingContextDTO {
    mode: string;
    entityId: number;
}

/**
 * DTO for sending messages to Iris with optional uncommitted file changes and optional context information.
 * Matches the server IrisMessageRequestDTO structure.
 */
export interface IrisMessageRequestDTO {
    content: IrisMessageContentDTO[];
    messageDifferentiator?: number;
    uncommittedFiles: { [path: string]: string };
    /** Context switch to apply atomically before the message is saved. */
    pendingContext?: IrisPendingContextDTO;
    /** What the user is currently viewing; forwarded to Pyris, not persisted. */
    context?: IrisMessageContextDTO[];
    /** Identifies the sending browser tab, so a command Iris issues while answering is addressed back to it. */
    clientId?: string;
}
