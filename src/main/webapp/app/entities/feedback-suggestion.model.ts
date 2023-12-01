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
