import { ExamLiveEventType } from 'app/exam/overview/services/exam-participation-live-events.service';

export const USER_DISPLAY_RELEVANT_EVENTS = [
    ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
    ExamLiveEventType.WORKING_TIME_UPDATE,
    ExamLiveEventType.EXAM_ATTENDANCE_CHECK,
    ExamLiveEventType.PROBLEM_STATEMENT_UPDATE,
];
export const USER_DISPLAY_RELEVANT_EVENTS_REOPEN = [ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT, ExamLiveEventType.WORKING_TIME_UPDATE, ExamLiveEventType.PROBLEM_STATEMENT_UPDATE];
