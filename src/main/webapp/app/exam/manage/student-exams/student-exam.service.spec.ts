import { TestBed } from '@angular/core/testing';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('Student Exam Service', () => {
    let httpMock: HttpTestingController;
    let service: StudentExamService;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useValue: { setAccessRightsForCourse: vi.fn() } }, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(StudentExamService);
        accountService = TestBed.inject(AccountService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it.each([{ exam: { course: { id: 1 } } }, { exam: { course: undefined } }, { exam: undefined }, undefined])(
        'should fetch and process an exam correctly on find and updateWorkingTime',
        (payloadStudentExam) => {
            let found: unknown;
            service.find(1, 2, 3).subscribe((result) => (found = result));

            const getRequest = httpMock.expectOne('api/exam/courses/1/exams/2/student-exams/3');
            expect(getRequest.request.method).toBe('GET');
            const findBody = { studentExam: payloadStudentExam } as StudentExamWithGradeDTO;
            getRequest.flush(findBody);
            expect((found as { body: unknown }).body).toEqual(findBody);
            expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payloadStudentExam?.exam?.course ? 1 : 0);

            let updated: unknown;
            service.updateWorkingTime(1, 2, 3, 10).subscribe((result) => (updated = result));

            const patchRequest = httpMock.expectOne('/api/exam/courses/1/exams/2/student-exams/3/working-time');
            expect(patchRequest.request.method).toBe('PATCH');
            expect(patchRequest.request.body).toBe(10);
            patchRequest.flush(payloadStudentExam ?? {});
            expect(updated).toEqual(payloadStudentExam ?? {});
            expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payloadStudentExam?.exam?.course ? 2 : 0);
        },
    );
});
