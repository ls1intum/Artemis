import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';
import { ForwardedMessageService } from '../../../../../main/webapp/app/shared/metis/forwarded-message.service';
import { PostingType } from '../../../../../main/webapp/app/entities/metis/posting.model';

describe('ForwardedMessageService', () => {
    let service: ForwardedMessageService;
    let httpMock: HttpTestingController;

    const apiUrl = 'api/forwarded-messages';

    const sampleForwardedMessage = new ForwardedMessage(1, 2, PostingType.POST, { id: 3 } as any, undefined, '');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [ForwardedMessageService],
        });

        service = TestBed.inject(ForwardedMessageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('createForwardedMessage', () => {
        it('should call POST API to create a new forwarded message', () => {
            service.createForwardedMessage(sampleForwardedMessage).subscribe((res) => {
                expect(res.body).toEqual(sampleForwardedMessage);
            });

            const req = httpMock.expectOne(`${apiUrl}`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(sampleForwardedMessage);
            req.flush(sampleForwardedMessage);
        });
    });

    describe('getForwardedMessages', () => {
        it('should call GET API to retrieve forwarded messages with type "post"', () => {
            const ids = [2, 3];
            const expectedResponse = [
                { id: 2, messages: [sampleForwardedMessage] },
                { id: 3, messages: [] },
            ];

            service.getForwardedMessages(ids, 'post').subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            const req = httpMock.expectOne((request) => {
                return request.url === `${apiUrl}` && request.params.get('ids') === '2,3' && request.params.get('type') === 'post';
            });

            expect(req.request.method).toBe('GET');
            req.flush(expectedResponse);
        });

        it('should call GET API to retrieve forwarded messages with type "answer"', () => {
            const ids = [4, 5];
            const expectedResponse = [
                { id: 4, messages: [] },
                { id: 5, messages: [sampleForwardedMessage] },
            ];

            service.getForwardedMessages(ids, 'answer').subscribe((res) => {
                expect(res.body).toEqual(expectedResponse);
            });

            const req = httpMock.expectOne((request) => {
                return request.url === `${apiUrl}` && request.params.get('ids') === '4,5' && request.params.get('type') === 'answer';
            });

            expect(req.request.method).toBe('GET');
            req.flush(expectedResponse);
        });

        it('should not make a GET request if IDs are empty', () => {
            return new Promise<void>((done) => {
                const ids: number[] = [];

                service.getForwardedMessages(ids, 'post').subscribe({
                    next: () => {},
                    error: (err) => {
                        expect(err.message).toBe('IDs cannot be empty');
                        done();
                    },
                });
            });
        });
    });
});
