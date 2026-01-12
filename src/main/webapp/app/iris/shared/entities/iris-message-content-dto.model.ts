/**
 * DTO for IrisMessageContent to match the backend structure.
 * Used when sending messages to the server.
 */
export class IrisMessageContentDTO {
    type?: 'text' | 'json';
    textContent?: string;
    jsonContent?: string;

    /**
     * Creates a text content DTO
     * @param content the text content
     */
    static text(content: string): IrisMessageContentDTO {
        return {
            type: 'text',
            textContent: content,
        };
    }

    /**
     * Creates a JSON content DTO
     * @param content the JSON content as string
     */
    static json(content: string): IrisMessageContentDTO {
        return {
            type: 'json',
            jsonContent: content,
        };
    }
}
