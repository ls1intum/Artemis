import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { mockClientMessage, mockConversation, mockServerMessage } from '../../helpers/sample/iris-sample-data';
import { IrisHttpChatMessageService } from 'app/iris/http-chat-message.service';
import { IrisUserMessage } from 'app/entities/iris/iris-message.model';

describe('Iris Http Chat Message Service', () => {
    let service: IrisHttpChatMessageService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisHttpChatMessageService],
        });
        service = TestBed.inject(IrisHttpChatMessageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a message', fakeAsync(() => {
            const returnedFromService = { ...mockClientMessage, id: 0 };
            const expected = { ...returnedFromService, id: 0 };
            service
                .createMessage(2, new IrisUserMessage())
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).toEqual(expected);
                    expect(resp.body!.id).toEqual(expected.id);
                });
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should resend a message', fakeAsync(() => {
            const returnedFromService = { ...mockClientMessage, id: 0 };
            const expected = returnedFromService;
            service
                .resendMessage(mockConversation.id, returnedFromService)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).toEqual(expected);
                    expect(resp.body!.id).toEqual(expected.id);
                });
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all messages for a session', fakeAsync(() => {
            const returnedFromService = [mockClientMessage, mockServerMessage];
            const expected = returnedFromService;
            service
                .getMessages(mockConversation.id)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update message helpful field', fakeAsync(() => {
            const returnedFromService = { ...mockServerMessage, helpful: true };
            const expected = returnedFromService;
            service
                .rateMessage(mockConversation.id, mockServerMessage.id, true)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        afterEach(() => {
            httpMock.verify();
        });
    });
});
