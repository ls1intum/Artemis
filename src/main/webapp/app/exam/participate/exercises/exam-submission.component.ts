export abstract class ExamSubmissionComponent {
    abstract hasUnsavedChanges(): boolean;
    abstract updateSubmissionFromView(intervalSave: boolean): void;

    /**
     * is called when the component becomes active / visible
     */
    abstract onActivate(): void;
}
