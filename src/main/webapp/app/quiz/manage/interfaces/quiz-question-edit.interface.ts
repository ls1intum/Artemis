export interface QuizQuestionEdit {
    /**
     * reset the question and calls the parsing method of the markdown editor
     */
    prepareForSave(): void;
}
