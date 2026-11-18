import dayjs from 'dayjs/esm';

/**
 * A lecture as the course overview sidebar needs it, returned by
 * `GET api/lecture/courses/{courseId}/lectures-for-overview`.
 *
 * Deliberately not the full `Lecture`: attachments are eagerly mapped on the server entity, so requesting whole
 * lectures loaded and serialised them on every course visit even though the sidebar renders none of them. They are
 * loaded by the lecture detail page instead.
 */
export interface LectureForOverview {
    id: number;
    title?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    isTutorialLecture?: boolean;
}
