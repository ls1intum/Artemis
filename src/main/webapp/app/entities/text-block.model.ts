import { sha1Hex } from 'app/shared/util/crypto.utils';
import { TextSubmission } from 'app/entities/text-submission.model';

export enum TextBlockType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
}

export class TextBlock {
    id?: string;
    text?: string;
    startIndex?: number;
    endIndex?: number;
    submission?: TextSubmission;
    type?: TextBlockType;

    /**
     * Identical with de.tum.in.www1.artemis.domain.text.TextBlock:computeId
     */
    computeId(): void {
        const submissionId = this.submission ? this.submission.id : 0;
        const idString = `${submissionId};${this.startIndex}-${this.endIndex};${this.text}`;
        this.id = sha1Hex(idString);
    }

    setTextFromSubmission(submission?: TextSubmission): void {
        this.submission = submission || this.submission;
        if (this.submission && !(this.startIndex === undefined || this.startIndex === null)) {
            this.text = this.submission.text?.substring(this.startIndex, this.endIndex) || '';
        }
    }
}
