import { TestBed } from '@angular/core/testing';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpClient, HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
describe('Student Exam Service', () => {
    setupTestBed({ zoneless: true });

    let httpClient: any;
    let httpClientPutSpy: any;
    let service: StudentExamService;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: AccountService,
                    useValue: {
                        setAccessRightsForCourse: vi.fn(),
                    },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        service = TestBed.inject(StudentExamService);
        accountService = TestBed.inject(AccountService);
        httpClient = TestBed.inject(HttpClient);
        httpClientPutSpy = vi.spyOn(httpClient, 'put');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call correct url if toggling submitted state and unsubmit is false', () => {
        service.toggleSubmittedState(1, 2, 3, false);
        expect(httpClientPutSpy).toHaveBeenCalledOnce();
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-submitted');
    });

    it('should call correct url if toggling submitted state and unsubmit is true', () => {
        service.toggleSubmittedState(1, 2, 3, true);
        expect(httpClientPutSpy).toHaveBeenCalledOnce();
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-unsubmitted');
    });

    it.each([
        {
            exam: {
                course: {
                    id: 1,
                },
            },
        },
        {
            exam: {
                course: undefined,
            },
        },
        {
            exam: undefined,
        },
        undefined,
    ] as StudentExam[])('should fetch and process an exam correctly on find and updateWorkingTime', (payloadExam) => {
        const findResponse = new HttpResponse<StudentExamWithGradeDTO>({ body: { studentExam: payloadExam } as StudentExamWithGradeDTO });
        const getSpy = vi.spyOn(httpClient, 'get').mockReturnValue(of(findResponse));

        let returnedExam;
        service.find(1, 2, 3).subscribe((result) => (returnedExam = result));

        expect(getSpy).toHaveBeenCalledOnce();
        expect(getSpy).toHaveBeenCalledWith(`api/exam/courses/1/exams/2/student-exams/3`, { observe: 'response' });
        expect(returnedExam).toBe(findResponse);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payloadExam?.exam?.course ? 1 : 0);

        const updateResponse = new HttpResponse<StudentExam>({ body: payloadExam });
        const patchSpy = vi.spyOn(httpClient, 'patch').mockReturnValue(of(updateResponse));
        service.updateWorkingTime(1, 2, 3, 10).subscribe((result) => (returnedExam = result));

        expect(patchSpy).toHaveBeenCalledOnce();
        expect(patchSpy).toHaveBeenCalledWith(`api/exam/courses/1/exams/2/student-exams/3/working-time`, 10, { observe: 'response' });
        expect(returnedExam).toBe(updateResponse);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payloadExam?.exam?.course ? 2 : 0);
    });

    it('should fetch and process exams correctly on findAllForExam', () => {
        const payload = [
            {
                exam: {
                    course: {
                        id: 1,
                    },
                },
            },
            {
                exam: {
                    course: {
                        id: 1,
                    },
                },
            },
            {
                exam: {
                    course: undefined,
                },
            },
            {
                exam: undefined,
            },
        ] as StudentExam[];
        const response = new HttpResponse<StudentExam[]>({ body: payload });
        const getSpy = vi.spyOn(httpClient, 'get').mockReturnValue(of(response));

        let returnedExams;
        service.findAllForExam(1, 2).subscribe((result) => (returnedExams = result));

        expect(getSpy).toHaveBeenCalledOnce();
        expect(getSpy).toHaveBeenCalledWith(`api/exam/courses/1/exams/2/student-exams`, { observe: 'response' });
        expect(returnedExams).toBe(response);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(2);
    });
});
