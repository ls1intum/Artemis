export class TextFeedbackSuggestion {
    constructor(
        public id: number | undefined,
        public exerciseId: number,
        public submissionId: number,
        public title: string,
        public description: string,
        public credits: number,
        public structuredGradingInstructionId: number | undefined,
        public indexStart: number | undefined,
        public indexEnd: number | undefined,
    ) {}
}

export class ProgrammingFeedbackSuggestion {
    constructor(
        public id: number | undefined,
        public exerciseId: number,
        public submissionId: number,
        public title: string,
        public description: string,
        public credits: number,
        public structuredGradingInstructionId: number | undefined,
        public filePath: string,
        public lineStart: number | undefined,
        public lineEnd: number | undefined,
    ) {}
}

export class ModelingFeedbackSuggestion {
    /**
     * Create a modeling feedback suggestions
     *
     * @param id The ID of the suggestions
     * @param exerciseId The ID of the exercise the submission was made for
     * @param submissionId The ID of the submissions this feedback has been created for
     * @param title The title of the suggestions
     * @param description A detailed description of the suggestion
     * @param credits The number of credits awarded as part of this suggestion
     * @param structuredGradingInstructionId The ID of the structured grading instruction this suggestion is related to if a corresponding grading instruction exists
     * @param elementIds The IDs of elements referenced by the suggestion
     */
    constructor(
        public id: number | undefined,
        public exerciseId: number,
        public submissionId: number,
        public title: string,
        public description: string,
        public credits: number,
        public structuredGradingInstructionId: number | undefined,
        public reference: string | undefined,
    ) {}
}
