import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

describe('Student Exam Service', () => {
    let httpClient: any;
    let httpClientPutSpy: any;
    let service: StudentExamService;

    beforeEach(() => {
        httpClient = {
            put: jest.fn(),
        };
        httpClientPutSpy = jest.spyOn(httpClient, 'put');
        service = new StudentExamService(new MockRouter() as any as Router, httpClient as HttpClient);
    });

    it('should call correct url if toggling submitted state and unsubmit is false', () => {
        service.toggleSubmittedState(1, 2, 3, false);
        expect(httpClientPutSpy).toHaveBeenCalledTimes(1);
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-submitted');
    });

    it('should call correct url if toggling submitted state and unsubmit is true', () => {
        service.toggleSubmittedState(1, 2, 3, true);
        expect(httpClientPutSpy).toHaveBeenCalledTimes(1);
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-unsubmitted');
    });
});
