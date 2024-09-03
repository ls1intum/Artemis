import { sha1Hex } from 'app/shared/util/crypto.utils';
import { TextSubmission } from 'app/entities/text/text-submission.model';

export enum TextBlockType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
}

export class TextBlock {
    id?: string;
    type?: TextBlockType;

    // The ID of the text block is computed as a hash of the submission ID, the start and end index and the text.
    // We need to keep it up to date with the latest values of these properties. Therefore, we use setter properties to never forget to update the ID.
    private _submissionId?: number;
    private _startIndex?: number;
    private _endIndex?: number;
    private _text?: string;

    set submissionId(value: number | undefined) {
        this._submissionId = value;
        this.computeId();
    }

    get submissionId(): number | undefined {
        return this._submissionId;
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
        const submissionId = this.submissionId ?? 0;
        const idString = `${submissionId};${this.startIndex}-${this.endIndex};${this.text}`;
        this.id = sha1Hex(idString);
    }

    setTextFromSubmission(submission?: TextSubmission): void {
        this.submissionId ??= submission?.id;
        if (submission && this.startIndex != undefined) {
            this.text = submission.text?.substring(this.startIndex, this.endIndex) || '';
        }
    }
}
