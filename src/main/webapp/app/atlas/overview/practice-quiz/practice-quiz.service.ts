import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PracticeSessionDTO, PracticeSessionReviewDTO, PracticeSessionSummaryDTO, PracticeSubmissionDTO } from 'app/atlas/overview/practice-quiz/practice-quiz.model';

@Service()
export class PracticeQuizService {
    private readonly http = inject(HttpClient);

    private readonly resourceUrl = 'api/atlas/courses';

    /**
     * Start a new practice quiz session for the given course.
     */
    startSession(courseId: number): Observable<PracticeSessionDTO> {
        return this.http.post<PracticeSessionDTO>(`${this.resourceUrl}/${courseId}/smart-quizzes/start`, null);
    }

    /**
     * Retrieve the currently in-progress session for the course, if one exists.
     */
    getCurrentSession(courseId: number): Observable<PracticeSessionDTO | null> {
        return this.http.get<PracticeSessionDTO | null>(`${this.resourceUrl}/${courseId}/smart-quizzes/current`);
    }

    /**
     * Submit answers for an active practice quiz session.
     */
    submitSession(courseId: number, sessionId: number, submission: PracticeSubmissionDTO): Observable<PracticeSessionReviewDTO> {
        return this.http.post<PracticeSessionReviewDTO>(`${this.resourceUrl}/${courseId}/smart-quizzes/sessions/${sessionId}/submit`, submission);
    }

    /**
     * Get review and detailed feedback for a completed practice session.
     */
    getSessionReview(courseId: number, sessionId: number): Observable<PracticeSessionReviewDTO> {
        return this.http.get<PracticeSessionReviewDTO>(`${this.resourceUrl}/${courseId}/smart-quizzes/sessions/${sessionId}`);
    }

    /**
     * Get the history of past practice quiz sessions for the student in this course.
     */
    getPastSessions(courseId: number): Observable<PracticeSessionSummaryDTO[]> {
        return this.http.get<PracticeSessionSummaryDTO[]>(`${this.resourceUrl}/${courseId}/smart-quizzes/sessions`);
    }
}
