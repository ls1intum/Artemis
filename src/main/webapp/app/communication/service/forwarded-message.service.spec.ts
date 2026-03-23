import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { lastValueFrom, of } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ForwardedMessageService } from 'app/communication/service/forwarded-message.service';
import { ForwardedMessage } from 'app/communication/shared/entities/forwarded-message.model';
import { PostingType } from 'app/communication/shared/entities/posting.model';

describe('ForwardedMessageService', () => {
    setupTestBed({ zoneless: true });

    let service: ForwardedMessageService;
    let httpClientMock: Partial<HttpClient>;

    const apiUrl = 'api/communication/forwarded-messages';
    const sampleForwardedMessage = new ForwardedMessage(1, 2, PostingType.POST, { id: 3 } as any, undefined, '');

    beforeEach(() => {
        httpClientMock = {
            post: vi.fn(),
            get: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [ForwardedMessageService, { provide: HttpClient, useValue: httpClientMock }],
        });

        service = TestBed.inject(ForwardedMessageService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('createForwardedMessage', () => {
        it('should call POST with a converted DTO in params', () => {
            const response = new HttpResponse<ForwardedMessage>({ body: sampleForwardedMessage });
            (httpClientMock.post as ReturnType<typeof vi.fn>).mockReturnValue(of(response));

            service.createForwardedMessage(sampleForwardedMessage).subscribe((res) => {
                expect(res.body).toEqual(sampleForwardedMessage);
            });

            expect(httpClientMock.post).toHaveBeenCalledOnce();

            const expectedDto = {
                id: 1,
                sourceId: 2,
                sourceType: PostingType.POST,
                destinationPostId: 3,
                destinationAnswerPostId: undefined,
                content: '',
            };

            expect(httpClientMock.post).toHaveBeenCalledWith(apiUrl, expectedDto, {
                observe: 'response',
            });
        });
    });

    describe('getForwardedMessages', () => {
        it('should call GET with type "POST"', () => {
            const ids = [2, 3];
            const expectedResponse = [
                { id: 2, messages: [sampleForwardedMessage] },
                { id: 3, messages: [] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as ReturnType<typeof vi.fn>).mockReturnValue(of(response));

            service.getForwardedMessages(ids, PostingType.POST).subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledOnce();

            const [calledUrl, calledOptions] = (httpClientMock.get as ReturnType<typeof vi.fn>).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);

            const expectedParams = {
                postingIds: '2,3',
            };

            expect(calledOptions.params.get('postingIds')).toBe(expectedParams.postingIds);
            expect(calledOptions.params.get('type')).toBe('POST');
            expect(calledOptions.observe).toBe('response');
        });

        it('should call GET with type "answer"', () => {
            const ids = [4, 5];
            const expectedResponse = [
                { id: 4, messages: [] },
                { id: 5, messages: [sampleForwardedMessage] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as ReturnType<typeof vi.fn>).mockReturnValue(of(response));

            service.getForwardedMessages(ids, PostingType.ANSWER).subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledOnce();

            const [calledUrl, calledOptions] = (httpClientMock.get as ReturnType<typeof vi.fn>).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);

            const expectedParams = {
                postingIds: '4,5',
            };

            expect(calledOptions.params.get('postingIds')).toBe(expectedParams.postingIds);
            expect(calledOptions.params.get('type')).toBe('ANSWER');
        });

        it('should throw an error if IDs are empty', async () => {
            const ids: number[] = [];

            await expect(lastValueFrom(service.getForwardedMessages(ids, PostingType.POST))).rejects.toThrow('IDs cannot be empty');
            expect(httpClientMock.get).not.toHaveBeenCalled();
        });
    });
});
