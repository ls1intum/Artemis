export interface PlagiarismPostCreationDTO {
    title?: string;
    content?: string;
    visibleForStudents?: boolean;
    hasForwardedMessages?: boolean;
    plagiarismCaseId?: number;
}
