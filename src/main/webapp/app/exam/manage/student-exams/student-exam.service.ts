import { Service, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { StudentExamApi } from 'app/openapi/api/student-exam-api';
import type { StudentExam } from 'app/openapi/model/student-exam';
import { AccountService } from 'app/core/auth/account.service';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { StudentExamOrDTO } from 'app/exam/shared/entities/student-exam-dto.model';

@Service()
export class StudentExamService {
    private studentExamApi = inject(StudentExamApi);
    private accountService = inject(AccountService);

    /**
     * Find a student exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     */
    find(courseId: number, examId: number, studentExamId: number): Observable<StudentExamWithGradeDTO> {
        return this.studentExamApi.getStudentExam(courseId, examId, studentExamId).pipe(
            map((studentExamWithGrade) => hydrate(new StudentExamWithGradeDTO(), studentExamWithGrade)),
            tap((studentExamWithGrade) => this.processStudentExam(studentExamWithGrade.studentExam)),
        );
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
    updateWorkingTime(courseId: number, examId: number, studentExamId: number, workingTime: number): Observable<StudentExam> {
        return this.studentExamApi.updateWorkingTime(courseId, examId, studentExamId, workingTime).pipe(tap((studentExam) => this.processStudentExam(studentExam)));
    }

    private processStudentExam(studentExam?: StudentExamOrDTO | StudentExam) {
        if (studentExam?.exam?.course) {
            this.accountService.setAccessRightsForCourse(studentExam.exam.course);
        }
    }
}
