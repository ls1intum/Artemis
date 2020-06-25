export abstract class ExamSubmissionComponent {
    abstract hasUnsavedChanges(): boolean;
    abstract updateSubmissionFromView(): void;
}
