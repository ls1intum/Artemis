import { TextSubmission } from 'app/entities/text-submission';

export class TextBlock {
    id?: number;
    text: string;
    startIndex: number;
    endIndex: number;
    submission?: TextSubmission;
}
