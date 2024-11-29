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

    describe('#createForwardedMessage', () => {
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

    describe('#getForwardedMessages', () => {
        it('should call GET API to retrieve forwarded messages for given post IDs', () => {
            const postIds = [2, 3];
            const expectedResponse: Map<number, ForwardedMessage[]> = new Map();
            expectedResponse.set(2, [sampleForwardedMessage]);
            expectedResponse.set(3, []);

            service.getForwardedMessages(postIds).subscribe((res) => {
                const body = res.body;
                expect(body).toEqual(expectedResponse);
            });

            const req = httpMock.expectOne((request) => {
                return request.url === `api/forwarded-messages/posts` && request.params.get('dest_post_ids') === '2,3';
            });

            expect(req.request.method).toBe('GET');
            req.flush(expectedResponse);
        });
    });
});
