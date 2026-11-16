import { EMPTY, Observable, asyncScheduler } from 'rxjs';
import { catchError, concatMap, map, throttleTime } from 'rxjs/operators';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';

/** The shortest time between two reloads of the number of generated student exams while students keep starting a test exam. */
export const GENERATED_STUDENT_EXAMS_REFRESH_INTERVAL_MS = 5000;

/**
 * Reloads the number of generated student exams whenever a test exam is started, without a reload for every single start.
 *
 * The student exam of a test exam is created when the student opens the start page, and the start event comes later, so a start does not tell how many student exams exist.
 * The checklist does, but it is expensive: the server counts participations and assessments of every exercise. A burst of starts therefore reloads it at most once per
 * {@link GENERATED_STUDENT_EXAMS_REFRESH_INTERVAL_MS}, the reload for the last start of the burst is not dropped, and reloads never run at the same time.
 *
 * @param starts emits the exam each time a student starts it
 * @param loadStatistics loads the checklist of the exam from the server
 * @returns the number of generated student exams after each reload
 */
export function generatedStudentExamsAfterStarts(starts: Observable<Exam>, loadStatistics: (exam: Exam) => Observable<ExamChecklist>): Observable<number> {
    return starts.pipe(
        throttleTime(GENERATED_STUDENT_EXAMS_REFRESH_INTERVAL_MS, asyncScheduler, { leading: true, trailing: true }),
        // A reload that is under way is finished before the next one begins, and a failed one only skips that update.
        concatMap((exam) => loadStatistics(exam).pipe(catchError(() => EMPTY))),
        map((examStatistics) => examStatistics.numberOfGeneratedStudentExams ?? 0),
    );
}
