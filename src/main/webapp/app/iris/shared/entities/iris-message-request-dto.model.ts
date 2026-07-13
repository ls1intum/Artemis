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
export class IrisMessageRequestDTO {
    content: IrisMessageContentDTO[];
    messageDifferentiator?: number;
    uncommittedFiles: { [path: string]: string };
    pendingContext?: IrisPendingContextDTO;
    context?: IrisMessageContextDTO[];

    constructor(
        content: IrisMessageContentDTO[],
        messageDifferentiator?: number,
        uncommittedFiles: { [path: string]: string } = {},
        pendingContextOrContext?: IrisPendingContextDTO | IrisMessageContextDTO[],
        context?: IrisMessageContextDTO[],
    ) {
        this.content = content;
        this.messageDifferentiator = messageDifferentiator;
        this.uncommittedFiles = uncommittedFiles;
        if (Array.isArray(pendingContextOrContext)) {
            this.context = pendingContextOrContext;
        } else {
            this.pendingContext = pendingContextOrContext;
            this.context = context;
        }
    }
}
