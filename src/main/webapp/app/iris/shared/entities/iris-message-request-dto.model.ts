import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';

/**
 * DTO for sending messages to Iris with optional uncommitted file changes.
 * Matches the server IrisMessageRequestDTO structure.
 */
export class IrisMessageRequestDTO {
    content: IrisMessageContentDTO[];
    messageDifferentiator?: number;
    uncommittedFiles: { [path: string]: string };

    constructor(content: IrisMessageContentDTO[], messageDifferentiator?: number, uncommittedFiles: { [path: string]: string } = {}) {
        this.content = content;
        this.messageDifferentiator = messageDifferentiator;
        this.uncommittedFiles = uncommittedFiles;
    }
}
