import { TextSubmission } from 'app/entities/text-submission';
import { sha1Hex } from 'app/utils/crypto.utils';

export class TextBlock {
    id: string;
    text: string;
    startIndex: number;
    endIndex: number;
    submission?: TextSubmission;

    /**
     * Identical with de.tum.in.www1.artemis.domain.TextBlock:computeId
     */
    computeId(): void {
        const submissionId = this.submission ? this.submission.id : 0;
        const idString = `${submissionId};${this.startIndex}-${this.endIndex};${this.text}`;
        this.id = sha1Hex(idString);
    }

    setTextFromSubmission(): void {
        if (this.submission) {
            this.text = this.submission.text.substring(this.startIndex, this.endIndex);
        }
    }
}
