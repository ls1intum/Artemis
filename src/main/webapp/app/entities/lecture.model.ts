import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit, IngestionState } from 'app/entities/lecture-unit/attachmentUnit.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    visibleDate?: dayjs.Dayjs;
    attachments?: Attachment[];
    posts?: Post[];
    lectureUnits?: LectureUnit[];
    course?: Course;

    // helper attribute
    channelName?: string;
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;
    ingested?: IngestionState;

    constructor() {
        this.ingested = this.checkIngestionState();
    }

    private checkIngestionState(): IngestionState {
        if (!this.lectureUnits) {
            return IngestionState.NOT_STARTED;
        }

        const attachmentUnits = this.lectureUnits.filter((unit) => unit.type === 'attachment') as AttachmentUnit[];
        const allDone = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.DONE);
        const allNotStarted = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.NOT_STARTED);
        const allFailed = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.ERROR);

        if (allDone) return IngestionState.DONE;
        if (allFailed) return IngestionState.ERROR;
        if (allNotStarted) return IngestionState.NOT_STARTED;

        return IngestionState.PARTIALLY_INGESTED;
    }
    public updateIngestionState?(): void {
        if (this.checkIngestionState) {
            this.ingested = this.checkIngestionState();
        }
    }
}
