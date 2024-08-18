import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SimplifiedTask, TestcaseAnalysisService } from 'app/exercises/programming/manage/grading/testcase-analysis/testcase-analysis.service';
import { FeedbackDetailsWithResultIdsDTO } from 'app/exercises/shared/result/result.service';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';

describe('TestcaseAnalysisService', () => {
    let service: TestcaseAnalysisService;
    let httpMock: HttpTestingController;

    const feedbackDetailsMock: FeedbackDetailsWithResultIdsDTO = {
        feedbackDetails: [
            { detailText: 'Feedback 1', testCaseName: 'test1' },
            { detailText: 'Feedback 2', testCaseName: 'test2' },
        ],
        resultIds: [1, 2],
    };

    const simplifiedTasksMock: ProgrammingExerciseServerSideTask[] = [
        { taskName: 'Task 1', testCases: [{ testName: 'test1' }] as ProgrammingExerciseServerSideTask['testCases'] },
        { taskName: 'Task 2', testCases: [{ testName: 'test2' }] as ProgrammingExerciseServerSideTask['testCases'] },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [TestcaseAnalysisService],
        });

        service = TestBed.inject(TestcaseAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('getFeedbackDetailsForExercise', () => {
        it('should retrieve feedback details for a given exercise', () => {
            service.getFeedbackDetailsForExercise(1).subscribe((response) => {
                expect(response.body).toEqual(feedbackDetailsMock);
            });

            const req = httpMock.expectOne('api/exercises/1/feedback-details');
            expect(req.request.method).toBe('GET');
            req.flush(feedbackDetailsMock);
        });

        it('should handle errors while retrieving feedback details', () => {
            service.getFeedbackDetailsForExercise(1).subscribe({
                next: () => {},
                error: (error) => {
                    expect(error.status).toBe(500);
                },
            });

            const req = httpMock.expectOne('api/exercises/1/feedback-details');
            expect(req.request.method).toBe('GET');
            req.flush('Something went wrong', { status: 500, statusText: 'Server Error' });
        });
    });

    describe('getSimplifiedTasks', () => {
        it('should retrieve simplified tasks for a given exercise', () => {
            const expectedTasks: SimplifiedTask[] = [
                { taskName: 'Task 1', testCases: [{ testName: 'test1' }] },
                { taskName: 'Task 2', testCases: [{ testName: 'test2' }] },
            ];

            service.getSimplifiedTasks(1).subscribe((tasks) => {
                expect(tasks).toEqual(expectedTasks);
            });

            const req = httpMock.expectOne('api/programming-exercises/1/tasks-with-unassigned-test-cases');
            expect(req.request.method).toBe('GET');
            req.flush(simplifiedTasksMock);
        });

        it('should handle errors while retrieving simplified tasks', () => {
            service.getSimplifiedTasks(1).subscribe({
                next: () => {},
                error: (error) => {
                    expect(error.status).toBe(404);
                },
            });

            const req = httpMock.expectOne('api/programming-exercises/1/tasks-with-unassigned-test-cases');
            expect(req.request.method).toBe('GET');
            req.flush('Not Found', { status: 404, statusText: 'Not Found' });
        });
    });
});
