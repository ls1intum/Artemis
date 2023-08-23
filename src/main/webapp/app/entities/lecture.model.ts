import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { Post } from 'app/entities/metis/post.model';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

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

    constructor() {}
}

/**
 * Determines whether the provided lecture should have a channel name. This is not the case, if messaging in the course is disabled.
 * If messaging is enabled, a channel name should exist for newly created and imported lectures.
 *
 * @param lecture
 */
export function requiresChannelName(lecture: Lecture): boolean {
    // not required if messaging is disabled
    if (!isMessagingEnabled(lecture.course)) {
        return false;
    }

    // required on create (messaging is enabled)
    if (lecture.id === undefined) {
        return true;
    }

    // when editing, it is required if the lecture has a channel
    return lecture.channelName !== undefined;
}
