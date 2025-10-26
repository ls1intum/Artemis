import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';

export interface LectureVideo extends BaseEntity {
    lectureId?: number;
    videoId?: string;
    filename?: string;
    sizeBytes?: number;
    uploadedAt?: dayjs.Dayjs;
    playlistUrl?: string;
    durationSeconds?: number;
}
