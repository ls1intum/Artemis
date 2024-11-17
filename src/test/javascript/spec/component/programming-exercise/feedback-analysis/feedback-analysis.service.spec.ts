import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { provideHttpClient } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('FeedbackAnalysisService', () => {
    let service: FeedbackAnalysisService;
    let httpMock: HttpTestingController;

    const feedbackDetailsMock: FeedbackDetail[] = [
        {
            concatenatedFeedbackIds: [1, 2],
            detailText: 'Feedback 1',
            testCaseName: 'test1',
            count: 5,
            relativeCount: 25.0,
            taskName: '1',
            errorCategory: 'StudentError',
        },
        {
            concatenatedFeedbackIds: [3, 4],
            detailText: 'Feedback 2',
            testCaseName: 'test2',
            count: 3,
            relativeCount: 15.0,
            taskName: '2',
            errorCategory: 'StudentError',
        },
    ];

    const feedbackAnalysisResponseMock = {
        feedbackDetails: { resultsOnPage: feedbackDetailsMock, numberOfPages: 1 },
        totalItems: 2,
        taskNames: ['task1', 'task2'],
        testCaseNames: ['test1', 'test2'],
        errorCategories: ['StudentError'],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), FeedbackAnalysisService],
        });

        service = TestBed.inject(FeedbackAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('search', () => {
        it('should retrieve feedback details for a given exercise with concatenatedFeedbackIds', async () => {
            const pageable = {
                page: 1,
                pageSize: 10,
                searchTerm: '',
                sortingOrder: SortingOrder.ASCENDING,
                sortedColumn: 'detailText',
            };
            const filters = { tasks: [], testCases: [], occurrence: [], errorCategories: [] };
            const responsePromise = service.search(pageable, { exerciseId: 1, filters });

            const req = httpMock.expectOne(
                'api/exercises/1/feedback-details?page=1&pageSize=10&searchTerm=&sortingOrder=ASCENDING&sortedColumn=detailText&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=',
            );
            expect(req.request.method).toBe('GET');
            req.flush(feedbackAnalysisResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(feedbackAnalysisResponseMock);
            expect(result.feedbackDetails.resultsOnPage[0].concatenatedFeedbackIds).toStrictEqual([1, 2]);
            expect(result.feedbackDetails.resultsOnPage[1].concatenatedFeedbackIds).toStrictEqual([3, 4]);
        });
    });

    describe('getMaxCount', () => {
        it('should retrieve the max count for an exercise', async () => {
            const responsePromise = service.getMaxCount(1);

            const req = httpMock.expectOne('api/exercises/1/feedback-details-max-count');
            expect(req.request.method).toBe('GET');
            req.flush(10);

            const result = await responsePromise;
            expect(result).toBe(10);
        });
    });

    describe('getParticipationForFeedbackIds', () => {
        it('should retrieve paginated participation details for specified feedback IDs', async () => {
            const feedbackIds = [1, 2];
            const pageable = {
                page: 1,
                pageSize: 10,
                sortedColumn: 'participationId',
                sortingOrder: SortingOrder.ASCENDING,
            };
            const participationResponseMock = {
                content: [
                    {
                        courseId: 1,
                        participationId: 101,
                        firstName: 'John',
                        lastName: 'Doe',
                        login: 'johndoe',
                        repositoryName: 'repo1',
                    },
                    {
                        courseId: 1,
                        participationId: 102,
                        firstName: 'Jane',
                        lastName: 'Smith',
                        login: 'janesmith',
                        repositoryName: 'repo2',
                    },
                ],
                numberOfPages: 1,
                totalItems: 2,
            };

            const responsePromise = service.getParticipationForFeedbackIds(1, feedbackIds, pageable);

            const req = httpMock.expectOne('api/exercises/1/feedback-details-participation?page=1&pageSize=10&sortedColumn=participationId&sortingOrder=ASCENDING');
            expect(req.request.method).toBe('GET');
            req.flush(participationResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(participationResponseMock);
            expect(result.content[0].firstName).toBe('John');
            expect(result.content[1].firstName).toBe('Jane');
        });
    });

    describe('getAffectedStudentCount', () => {
        it('should retrieve the count of affected students for a feedback detail text', async () => {
            const exerciseId = 1;
            const feedbackDetailText = 'Test feedback detail';
            const affectedStudentCountMock = 42;

            const responsePromise = service.getAffectedStudentCount(exerciseId, feedbackDetailText);

            const req = httpMock.expectOne(`api/exercises/${exerciseId}/feedback-detail/affected-students?detailText=${encodeURIComponent(feedbackDetailText)}`);
            expect(req.request.method).toBe('GET');
            req.flush(affectedStudentCountMock);

            const result = await responsePromise;
            expect(result).toBe(affectedStudentCountMock);
        });
    });
});
