import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';
import { IrisMessageContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';

/**
 * DTO for sending messages to Iris with optional uncommitted file changes and optional context information.
 * Matches the server IrisMessageRequestDTO structure.
 */
export class IrisMessageRequestDTO {
    content: IrisMessageContentDTO[];
    messageDifferentiator?: number;
    uncommittedFiles: { [path: string]: string };
    context?: IrisMessageContextDTO[];

    constructor(content: IrisMessageContentDTO[], messageDifferentiator?: number, uncommittedFiles: { [path: string]: string } = {}, context?: IrisMessageContextDTO[]) {
        this.content = content;
        this.messageDifferentiator = messageDifferentiator;
        this.uncommittedFiles = uncommittedFiles;
        this.context = context;
    }
}
