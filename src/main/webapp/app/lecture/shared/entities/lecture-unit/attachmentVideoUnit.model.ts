import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';

export class AttachmentVideoUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;
    public slides?: Slide[];
    public videoSource?: string;
    public transcriptionProperties?: LectureTranscriptionDTO;

    constructor() {
        super(LectureUnitType.ATTACHMENT_VIDEO);
    }
}

export enum TranscriptionStatus {
    PENDING = 'PENDING',
    PROCESSING = 'PROCESSING',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
}

export interface LectureTranscriptionDTO {
    lectureUnitId: number;
    language: string;
    segments: TranscriptionSegment[];
}

export interface TranscriptionSegment {
    text?: string;
    startTime?: number;
    endTime?: number;
    slideNumber?: number;
}
