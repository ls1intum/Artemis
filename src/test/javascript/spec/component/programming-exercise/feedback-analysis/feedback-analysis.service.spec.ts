import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { provideHttpClient } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('FeedbackAnalysisService', () => {
    let service: FeedbackAnalysisService;
    let httpMock: HttpTestingController;

    const feedbackDetailsMock: FeedbackDetail[] = [
        { detailText: 'Feedback 1', testCaseName: 'test1', count: 5, relativeCount: 25.0, taskNumber: '1', errorCategory: 'StudentError' },
        { detailText: 'Feedback 2', testCaseName: 'test2', count: 3, relativeCount: 15.0, taskNumber: '2', errorCategory: 'StudentError' },
    ];

    const feedbackAnalysisResponseMock = {
        feedbackDetails: { resultsOnPage: feedbackDetailsMock, numberOfPages: 1 },
        totalItems: 2,
        totalAmountOfTasks: 2,
        testCaseNames: ['test1', 'test2'],
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
        it('should retrieve feedback details for a given exercise', async () => {
            const pageable = {
                page: 1,
                pageSize: 10,
                searchTerm: '',
                sortingOrder: SortingOrder.ASCENDING,
                sortedColumn: 'detailText',
            };
            const filters = { tasks: [], testCases: [], occurrence: [] };
            const responsePromise = service.search(pageable, { exerciseId: 1, filters });

            const req = httpMock.expectOne(
                'api/exercises/1/feedback-details?page=1&pageSize=10&searchTerm=&sortingOrder=ASCENDING&sortedColumn=detailText&filterTasks=&filterTestCases=&filterOccurrence=',
            );
            expect(req.request.method).toBe('GET');
            req.flush(feedbackAnalysisResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(feedbackAnalysisResponseMock);
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
});
