import { TestBed } from '@angular/core/testing';
import { lastValueFrom, of } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ForwardedMessageService } from 'app/shared/metis/forwarded-message.service';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';
import { PostingType } from 'app/entities/metis/posting.model';

describe('ForwardedMessageService', () => {
    let service: ForwardedMessageService;
    let httpClientMock: Partial<HttpClient>;

    const apiUrl = 'api/forwarded-messages';
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
        it('should call POST API to create a new forwarded message', () => {
            const response = new HttpResponse<ForwardedMessage>({ body: sampleForwardedMessage });
            (httpClientMock.post as jest.Mock).mockReturnValue(of(response));

            service.createForwardedMessage(sampleForwardedMessage).subscribe((res) => {
                expect(res.body).toEqual(sampleForwardedMessage);
            });

            expect(httpClientMock.post).toHaveBeenCalledOnce();
            expect(httpClientMock.post).toHaveBeenCalledWith(apiUrl, sampleForwardedMessage, { observe: 'response' });
        });
    });

    describe('getForwardedMessages', () => {
        it('should call GET API to retrieve forwarded messages with type "post"', () => {
            const ids = [2, 3];
            const expectedResponse = [
                { id: 2, messages: [sampleForwardedMessage] },
                { id: 3, messages: [] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as jest.Mock).mockReturnValue(of(response));

            service.getForwardedMessages(ids, 'post').subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledOnce();

            const [calledUrl, calledOptions] = (httpClientMock.get as jest.Mock).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);
            expect(calledOptions.params.get('ids')).toBe('2,3');
            expect(calledOptions.params.get('type')).toBe('post');
            expect(calledOptions.observe).toBe('response');
        });

        it('should call GET API to retrieve forwarded messages with type "answer"', () => {
            const ids = [4, 5];
            const expectedResponse = [
                { id: 4, messages: [] },
                { id: 5, messages: [sampleForwardedMessage] },
            ];
            const response = new HttpResponse({ body: expectedResponse });

            (httpClientMock.get as jest.Mock).mockReturnValue(of(response));

            service.getForwardedMessages(ids, 'answer').subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            expect(httpClientMock.get).toHaveBeenCalledOnce();
            const [calledUrl, calledOptions] = (httpClientMock.get as jest.Mock).mock.calls[0];
            expect(calledUrl).toBe(apiUrl);
            expect(calledOptions.params.get('ids')).toBe('4,5');
            expect(calledOptions.params.get('type')).toBe('answer');
        });

        it('should throw an error if IDs are empty', async () => {
            const ids: number[] = [];

            await expect(lastValueFrom(service.getForwardedMessages(ids, 'post'))).rejects.toThrow('IDs cannot be empty');

            expect(httpClientMock.get).not.toHaveBeenCalled();
        });
    });
});
