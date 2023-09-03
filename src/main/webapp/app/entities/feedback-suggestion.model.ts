import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

export class TextFeedbackSuggestion {
    constructor(
        public id: number | undefined,
        public exerciseId: number,
        public submissionId: number,
        public title: string,
        public description: string,
        public credits: number,
        public gradingInstruction: GradingInstruction | undefined,
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
        public gradingInstruction: GradingInstruction | undefined,
        public filePath: string,
        public lineStart: number | undefined,
        public lineEnd: number | undefined,
    ) {}
}
