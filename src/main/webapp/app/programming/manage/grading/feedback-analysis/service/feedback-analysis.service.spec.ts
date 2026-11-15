import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';

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
            hasLongFeedbackText: false,
        },
        {
            feedbackIds: [2],
            detailTexts: ['Feedback 2'],
            testCaseName: 'test2',
            count: 3,
            relativeCount: 15.0,
            taskName: '2',
            errorCategory: 'StudentError',
            hasLongFeedbackText: false,
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
                'api/assessment/exercises/1/feedback-details?page=1&pageSize=10&searchTerm=&sortingOrder=ASCENDING&sortedColumn=detailText&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=&groupFeedback=false',
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

            const req = httpMock.expectOne('api/assessment/exercises/1/feedback-details-max-count');
            expect(req.request.method).toBe('GET');
            req.flush(10);

            const result = await responsePromise;
            expect(result).toBe(10);
        });
    });

    describe('getParticipationForFeedbackDetailText', () => {
        it('should retrieve paginated participation details for up to 5 feedback details', async () => {
            const feedbackDetailsId = [1, 2, 3, 4, 5];
            const participationResponseMock = [
                {
                    participationId: 101,
                    firstName: 'A',
                    lastName: 'Z',
                    login: 'AZ',
                    repositoryName: 'repo1',
                },
                {
                    participationId: 102,
                    firstName: 'I',
                    lastName: 'B',
                    login: 'IB',
                    repositoryName: 'repo2',
                },
            ];

            const responsePromise = service.getParticipationForFeedbackDetailText(1, feedbackDetailsId);

            const req = httpMock.expectOne('api/assessment/exercises/1/feedback-details-participation?feedbackId1=1&feedbackId2=2&feedbackId3=3&feedbackId4=4&feedbackId5=5');
            expect(req.request.method).toBe('GET');
            req.flush(participationResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(participationResponseMock);
            expect(result[0].firstName).toBe('A');
            expect(result[1].firstName).toBe('I');
        });

        it('should handle less than 5 feedback details gracefully', async () => {
            const feedbackDetailsId = [1, 2];
            const participationResponseMock = [
                {
                    participationId: 101,
                    firstName: 'A',
                    lastName: 'Z',
                    login: 'AZ',
                    repositoryName: 'repo1',
                },
            ];

            const responsePromise = service.getParticipationForFeedbackDetailText(1, feedbackDetailsId);

            const req = httpMock.expectOne('api/assessment/exercises/1/feedback-details-participation?feedbackId1=1&feedbackId2=2');
            expect(req.request.method).toBe('GET');
            req.flush(participationResponseMock);

            const result = await responsePromise;
            expect(result).toEqual(participationResponseMock);
            expect(result[0].firstName).toBe('A');
        });
    });

    describe('createChannel', () => {
        const url = '/api/communication/courses/1/exercises/2/feedback-channel';

        it('sends a feedback channel request and hydrates the created channel', async () => {
            const channel = new ChannelDTO();
            channel.name = 'feedback-channel';
            channel.description = 'Discussion channel for feedback';
            channel.topic = 'FeedbackDiscussion';
            channel.isPublic = true;
            const feedbackDetailTexts = ['Sample feedback detail text'];
            const testCaseName = 'test1';

            const result = service.createChannel(1, 2, { channel, feedbackDetailTexts, testCaseName });
            const req = httpMock.expectOne({ method: 'POST', url });
            const settings = {
                type: 'channel',
                name: 'feedback-channel',
                description: 'Discussion channel for feedback',
                topic: 'FeedbackDiscussion',
                isPublic: true,
                isAnnouncementChannel: false,
                isArchived: false,
                isCourseWide: false,
            };
            expect(req.request.body).toEqual({ channel: settings, feedbackDetailTexts, testCaseName });
            req.flush(
                { ...settings, id: 1001, subType: 'feedbackDiscussion', creationDate: '2026-01-01T09:00:00Z' },
                { status: 201, statusText: 'Created', headers: { Location: '/api/channels/1001' } },
            );

            const created = await result;
            expect(created).toBeInstanceOf(ChannelDTO);
            expect(created).toMatchObject({ ...settings, id: 1001, subType: ChannelSubType.FEEDBACK_DISCUSSION });
            expect(created.creationDate?.toISOString()).toBe('2026-01-01T09:00:00.000Z');
        });

        it('preserves the HTTP error when feedback channel creation is forbidden', async () => {
            const result = service.createChannel(1, 2, { channel: new ChannelDTO(), feedbackDetailTexts: ['Sample feedback'], testCaseName: 'test1' });
            const rejection = expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
            httpMock.expectOne({ method: 'POST', url }).flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
            await rejection;
            await expect(result).rejects.toMatchObject({ status: 403, error: { message: 'Forbidden' } });
        });
    });
});
