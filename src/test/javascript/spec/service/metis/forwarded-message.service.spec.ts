import { TestBed } from '@angular/core/testing';
import { lastValueFrom, of } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { ForwardedMessageService } from 'app/shared/metis/forwarded-message.service';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';
import { PostingType } from 'app/entities/metis/posting.model';

describe('ForwardedMessageService', () => {
    let service: ForwardedMessageService;
    let httpClientMock: Partial<HttpClient>;

    const apiUrl = 'api/communication/forwarded-messages';
    const sampleForwardedMessage = new ForwardedMessage(1, 2, PostingType.POST, { id: 3 } as any, undefined, '');

    beforeEach(() => {
        httpClientMock = {
            post: jest.fn(),
            get: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [ForwardedMessageService, { provide: HttpClient, useValue: httpClientMock }],
        });

        service = TestBed.inject(ForwardedMessageService);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('createForwardedMessage', () => {
        it('should call POST with a converted DTO and courseId in params', () => {
            const response = new HttpResponse<ForwardedMessage>({ body: sampleForwardedMessage });
            const courseId = 99;
            (httpClientMock.post as jest.Mock).mockReturnValue(of(response));

            service.createForwardedMessage(sampleForwardedMessage, courseId).subscribe((res) => {
                expect(res.body).toEqual(sampleForwardedMessage);
            });

            expect(httpClientMock.post).toHaveBeenCalledTimes(1);

            const expectedDto = {
                id: 1,
                sourceId: 2,
                sourceType: 0, // numeric enum for POST
                destinationPostId: 3,
                destinationAnswerPostId: undefined,
                content: '',
            };

            const expectedParams = new HttpParams().set('courseId', '99');

            expect(httpClientMock.post).toHaveBeenCalledWith(apiUrl, expectedDto, {
                params: expectedParams,
                observe: 'response',
            });
        });
    });

    describe('getForwardedMessages', () => {
        it('should call GET with type "POST"', () => {
            const ids = [2, 3];
            const courseId = 99;
            const expectedResponse = [
                { id: 2, messages: [sampleForwardedMessage] },
                { id: 3, messages: [] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as jest.Mock).mockReturnValue(of(response));

            service.getForwardedMessages(ids, PostingType.POST, courseId).subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledTimes(1);

            const [calledUrl, calledOptions] = (httpClientMock.get as jest.Mock).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);

            const expectedParams = {
                courseId: '99',
                postingIds: '2,3',
            };

            expect(calledOptions.params.get('courseId')).toBe(expectedParams.courseId);
            expect(calledOptions.params.get('postingIds')).toBe(expectedParams.postingIds);
            expect(calledOptions.params.get('type')).toBe('POST');
            expect(calledOptions.observe).toBe('response');
        });

        it('should call GET with type "answer"', () => {
            const ids = [4, 5];
            const courseId = 99;
            const expectedResponse = [
                { id: 4, messages: [] },
                { id: 5, messages: [sampleForwardedMessage] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as jest.Mock).mockReturnValue(of(response));

            service.getForwardedMessages(ids, PostingType.ANSWER, courseId).subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledTimes(1);

            const [calledUrl, calledOptions] = (httpClientMock.get as jest.Mock).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);

            const expectedParams = {
                courseId: '99',
                postingIds: '4,5',
            };
            expect(calledOptions.params.get('courseId')).toBe(expectedParams.courseId);
            expect(calledOptions.params.get('postingIds')).toBe(expectedParams.postingIds);
            expect(calledOptions.params.get('type')).toBe('ANSWER');
        });

        it('should throw an error if IDs are empty', async () => {
            const ids: number[] = [];
            const courseId = 99;

            await expect(lastValueFrom(service.getForwardedMessages(ids, PostingType.POST, courseId))).rejects.toThrow('IDs cannot be empty');
            expect(httpClientMock.get).not.toHaveBeenCalled();
        });
    });
});
