export interface PlagiarismPostCreationDtoModel {
    title: string;
    content: string;
    visibleForStudents: boolean;
    hasForwardedMessages: boolean;
    plagiarismCaseId: number;
}
