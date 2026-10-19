import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';

export enum AttachmentUpdateIntent {
    NO_FILE_CHANGE = 'NO_FILE_CHANGE',
    FILE_UPLOAD = 'FILE_UPLOAD',
    EDITOR_PDF_CONTENT_CHANGED = 'EDITOR_PDF_CONTENT_CHANGED',
}

export class AttachmentVideoUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;
    public slides?: Slide[];
    public videoSource?: string;
    public videoSourceType?: 'TUM_LIVE' | 'YOUTUBE' | null;
    public youtubeVideoId?: string | null;
    public transcriptionProperties?: LectureTranscriptionDTO;
    public attachmentUpdateIntent?: AttachmentUpdateIntent;

    constructor() {
        super(LectureUnitType.ATTACHMENT_VIDEO);
    }
}

export enum TranscriptionStatus {
    PENDING = 'PENDING',
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
