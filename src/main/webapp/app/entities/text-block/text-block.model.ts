import { TextSubmission } from 'app/entities/text-submission';

export class TextBlock {
    id: string;
    text: string;
    startIndex: number;
    endIndex: number;
    submission?: TextSubmission;
}
