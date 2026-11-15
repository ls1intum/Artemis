/**
 * Name of the event that tells the exam management views that an exam is gone.
 * <p>
 * It is broadcast as soon as a deletion is confirmed, before the exam list is read from the server again, so that no view still shows or
 * requests data (for example the statistics of the exam status) of an exam that does not exist any more.
 */
export const EXAM_DELETED_EVENT = 'examDeleted';

/** Content of the {@link EXAM_DELETED_EVENT} event. */
export interface ExamDeletedEventContent {
    examId: number;
}
