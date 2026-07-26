import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { StudentExamDTO, StudentExamOrDTO } from 'app/exam/shared/entities/student-exam-dto.model';

type EntityResponseType = HttpResponse<StudentExamDTO>;

@Injectable({ providedIn: 'root' })
export class StudentExamService {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    public resourceUrl = 'api/exam/courses';

    /**
     * Find a student exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     */
    find(courseId: number, examId: number, studentExamId: number): Observable<HttpResponse<StudentExamWithGradeDTO>> {
        return this.http
            .get<StudentExamWithGradeDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}`, { observe: 'response' })
            .pipe(tap((res: HttpResponse<StudentExamWithGradeDTO>) => this.processStudentExam(res?.body?.studentExam)));
    }

    /**
     * Update the working time of the given student exam.
     *
     * The response includes a nested `exam` (with `course`, so {@link processStudentExam} can still set access
     * rights), but no `user`, `exercises`, or `examSessions`. Callers must not wholesale-replace an already-loaded
     * full student exam with this response; merge only the fields that can actually change (see
     * `StudentExamDetailComponent#saveWorkingTime`).
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     * @param workingTime The working time in seconds.
     */
    updateWorkingTime(courseId: number, examId: number, studentExamId: number, workingTime: number): Observable<EntityResponseType> {
        return this.http
            .patch<StudentExamDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/working-time`, workingTime, { observe: 'response' })
            .pipe(tap((res: EntityResponseType) => this.processStudentExam(res?.body ?? undefined)));
    }

    /**
     * Response contains only `id`, `submitted` and `submissionDate` (no `exam`, `user`, `exercises`, `examSessions`).
     */
    toggleSubmittedState(courseId: number, examId: number, studentExamId: number, unsubmit: boolean): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/toggle-to-`;
        if (unsubmit) {
            return this.http.put<StudentExamDTO>(url + `unsubmitted`, {}, { observe: 'response' });
        } else {
            return this.http.put<StudentExamDTO>(url + `submitted`, {}, { observe: 'response' });
        }
    }

    private processStudentExam(studentExam?: StudentExamOrDTO) {
        if (studentExam?.exam?.course) {
            this.accountService.setAccessRightsForCourse(studentExam.exam.course);
        }
    }

    /**
     * Get longest working time for the exam.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    getLongestWorkingTimeForExam(courseId: number, examId: number): Observable<number> {
        return this.http.get<number>(`${this.resourceUrl}/${courseId}/exams/${examId}/longest-working-time`);
    }
}
