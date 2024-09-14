import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FeedbackAnalysisResponse, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';

describe('FeedbackAnalysisService', () => {
    let service: FeedbackAnalysisService;
    let httpMock: HttpTestingController;

    const feedbackDetailsMock: FeedbackDetail[] = [
        { detailText: 'Feedback 1', testCaseName: 'test1', count: 5, relativeCount: 25.0, taskNumber: 1 },
        { detailText: 'Feedback 2', testCaseName: 'test2', count: 3, relativeCount: 15.0, taskNumber: 2 },
    ];

    const feedbackResponseMock: FeedbackAnalysisResponse = {
        feedbackDetails: { resultsOnPage: feedbackDetailsMock, numberOfPages: 1 },
        totalItems: 8,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [FeedbackAnalysisService],
        });

        service = TestBed.inject(FeedbackAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('search', () => {
        it('should retrieve feedback details for a given exercise', async () => {
            const pageable: SearchTermPageableSearch = {
                page: 1,
                pageSize: 10,
                searchTerm: '',
                sortingOrder: SortingOrder.ASCENDING,
                sortedColumn: 'count',
            };
            const exerciseId = 1;

            const responsePromise = service.search(pageable, { exerciseId });

            const req = httpMock.expectOne('api/exercises/1/feedback-details-paged');
            expect(req.request.method).toBe('POST');
            req.flush(feedbackResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(feedbackResponseMock);
        });
    });
});
