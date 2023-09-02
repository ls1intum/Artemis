import { sha1Hex } from 'app/shared/util/crypto.utils';
import { TextSubmission } from 'app/entities/text-submission.model';

export enum TextBlockType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
}

export class TextBlock {
    id?: string;
    type?: TextBlockType;

    // The ID of the text block is computed as a hash of the submission ID, the start and end index and the text.
    // We need to keep it up to date with the latest values of these properties. Therefore, we use setter properties to never forget to update the ID.
    private _submission?: TextSubmission;
    private _startIndex?: number;
    private _endIndex?: number;
    private _text?: string;

    set submission(value: TextSubmission | undefined) {
        this._submission = value;
        this.computeId();
    }

    get submission(): TextSubmission | undefined {
        return this._submission;
    }

    set startIndex(value: number | undefined) {
        this._startIndex = value;
        this.computeId();
    }

    get startIndex(): number | undefined {
        return this._startIndex;
    }

    set endIndex(value: number | undefined) {
        this._endIndex = value;
        this.computeId();
    }

    get endIndex(): number | undefined {
        return this._endIndex;
    }

    set text(value: string | undefined) {
        this._text = value;
        this.computeId();
    }

    get text(): string | undefined {
        return this._text;
    }

    /**
     * Computes the ID of the text block. The ID is a hash of the submission ID, the start and end index and the text.
     */
    private computeId(): void {
        const submissionId = this.submission?.id ?? 0;
        const idString = `${submissionId};${this.startIndex}-${this.endIndex};${this.text}`;
        this.id = sha1Hex(idString);
    }

    setTextFromSubmission(submission?: TextSubmission): void {
        this.submission ??= submission;
        if (this.submission && this.startIndex != undefined) {
            this.text = this.submission.text?.substring(this.startIndex, this.endIndex) || '';
        }
    }
}
