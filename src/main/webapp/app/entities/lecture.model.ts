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
    ingested: IngestionState;

    constructor() {
        this.updateIngestionState();
    }

    public updateIngestionState(): void {
        this.ingested = IngestionState.NOT_STARTED;
        if (this.lectureUnits) {
            const attachmentUnits = this.lectureUnits.filter((unit) => unit.type === 'attachment') as AttachmentUnit[];
            const allDone = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.DONE);
            const allNotStarted = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.NOT_STARTED);
            const allFailed = attachmentUnits.every((unit) => unit.pyrisIngestionState === IngestionState.ERROR);
            this.ingested = IngestionState.PARTIALLY_INGESTED;
            if (allDone) this.ingested = IngestionState.DONE;
            if (allFailed) this.ingested = IngestionState.ERROR;
            if (allNotStarted) this.ingested = IngestionState.NOT_STARTED;
        }
    }
}
