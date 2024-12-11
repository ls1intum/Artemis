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
            feedbackIds: [1],
            detailTexts: ['Feedback 1'],
            testCaseName: 'test1',
            count: 5,
            relativeCount: 25.0,
            taskName: '1',
            errorCategory: 'StudentError',
        },
        {
            feedbackIds: [2],
            detailTexts: ['Feedback 2'],
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
            const responsePromise = service.search(pageable, false, { exerciseId: 1, filters });

            const req = httpMock.expectOne(
                'api/exercises/1/feedback-details?page=1&pageSize=10&searchTerm=&sortingOrder=ASCENDING&sortedColumn=detailText&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=&groupFeedback=false',
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

    describe('getParticipationForFeedbackDetailText', () => {
        it('should retrieve paginated participation details for up to 5 feedback details', async () => {
            const feedbackDetailsId = [1, 2, 3, 4, 5];
            const pageable = {
                page: 1,
                pageSize: 10,
                sortedColumn: 'participationId',
                sortingOrder: SortingOrder.ASCENDING,
            };
            const participationResponseMock = {
                content: [
                    {
                        participationId: 101,
                        firstName: 'John',
                        lastName: 'Doe',
                        login: 'johndoe',
                        repositoryName: 'repo1',
                    },
                    {
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

            const responsePromise = service.getParticipationForFeedbackDetailText(1, feedbackDetailsId, 'test1', pageable);

            const req = httpMock.expectOne(
                'api/exercises/1/feedback-details-participation?page=1&pageSize=10&sortedColumn=participationId&sortingOrder=ASCENDING&testCaseName=test1&feedbackId1=1&feedbackId2=2&feedbackId3=3&feedbackId4=4&feedbackId5=5',
            );
            expect(req.request.method).toBe('GET');
            req.flush(participationResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(participationResponseMock);
            expect(result.content[0].firstName).toBe('John');
            expect(result.content[1].firstName).toBe('Jane');
        });

        it('should handle less than 5 feedback details gracefully', async () => {
            const feedbackDetailsId = [1, 2];
            const pageable = {
                page: 1,
                pageSize: 10,
                sortedColumn: 'participationId',
                sortingOrder: SortingOrder.ASCENDING,
            };
            const participationResponseMock = {
                content: [
                    {
                        participationId: 101,
                        firstName: 'John',
                        lastName: 'Doe',
                        login: 'johndoe',
                        repositoryName: 'repo1',
                    },
                ],
                numberOfPages: 1,
                totalItems: 1,
            };

            const responsePromise = service.getParticipationForFeedbackDetailText(1, feedbackDetailsId, 'test1', pageable);

            const req = httpMock.expectOne(
                'api/exercises/1/feedback-details-participation?page=1&pageSize=10&sortedColumn=participationId&sortingOrder=ASCENDING&testCaseName=test1&feedbackId1=1&feedbackId2=2',
            );
            expect(req.request.method).toBe('GET');
            req.flush(participationResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(participationResponseMock);
            expect(result.content[0].firstName).toBe('John');
        });
    });

    describe('createChannel', () => {
        it('should send a POST request to create a feedback-specific channel and return the created channel DTO', async () => {
            const courseId = 1;
            const exerciseId = 2;

            const channelDtoMock = {
                name: 'feedback-channel',
                description: 'Discussion channel for feedback',
                isPublic: true,
                isAnnouncementChannel: false,
            };

            const feedbackChannelRequestMock = {
                channel: channelDtoMock,
                feedbackDetailTexts: ['Sample feedback detail text'],
                testCaseName: 'test1',
            };

            const createdChannelMock = {
                id: 1001,
                name: 'feedback-channel',
                description: 'Discussion channel for feedback',
                isPublic: true,
                isAnnouncementChannel: false,
            };

            const responsePromise = service.createChannel(courseId, exerciseId, feedbackChannelRequestMock);

            const req = httpMock.expectOne(`api/courses/${courseId}/${exerciseId}/feedback-channel`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(feedbackChannelRequestMock);

            req.flush(createdChannelMock);

            const result = await responsePromise;
            expect(result).toEqual(createdChannelMock);
            expect(result.name).toBe('feedback-channel');
            expect(result.description).toBe('Discussion channel for feedback');
        });
    });
});
