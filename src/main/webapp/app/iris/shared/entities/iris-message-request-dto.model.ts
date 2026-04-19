import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';

/**
 * DTO for sending messages to Iris with optional uncommitted file changes and lecture context.
 * Matches the server IrisMessageRequestDTO structure.
 */
export class IrisMessageRequestDTO {
    content: IrisMessageContentDTO[];
    messageDifferentiator?: number;
    uncommittedFiles: { [path: string]: string };
    pdfPage?: number;
    videoTimestamp?: number;

    constructor(content: IrisMessageContentDTO[], messageDifferentiator?: number, uncommittedFiles: { [path: string]: string } = {}, pdfPage?: number, videoTimestamp?: number) {
        this.content = content;
        this.messageDifferentiator = messageDifferentiator;
        this.uncommittedFiles = uncommittedFiles;
        this.pdfPage = pdfPage;
        this.videoTimestamp = videoTimestamp;
    }
}
