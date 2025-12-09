import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { irisExercise, mockClientMessage, mockConversation, mockServerMessage } from 'test/helpers/sample/iris-sample-data';
import { IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { provideHttpClient } from '@angular/common/http';

describe('IrisChatHttpService', () => {
    let service: IrisChatHttpService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisChatHttpService],
        });
        service = TestBed.inject(IrisChatHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a message', fakeAsync(() => {
            const returnedFromService = Object.assign({}, mockClientMessage, { id: 0 });
            const expected = Object.assign({}, returnedFromService, { id: 0 });
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
            const returnedFromService = Object.assign({}, mockClientMessage, { id: 0 });
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
            const returnedFromService = [mockServerMessage, mockClientMessage];
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
            const returnedFromService = Object.assign({}, mockServerMessage, { helpful: true });
            const expected = returnedFromService;
            service
                .rateMessage(mockConversation.id, mockServerMessage.id, true)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should create a session', fakeAsync(() => {
            const returnedFromService = { id: '1' };
            service
                .createSession(ChatServiceMode.COURSE + '/' + 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(returnedFromService));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService, { status: 201, statusText: 'Created' });
            tick();
        }));

        it('should return current session', fakeAsync(() => {
            const returnedFromService = mockConversation;
            const expected = returnedFromService;
            service
                .getCurrentSessionOrCreateIfNotExists(ChatServiceMode.PROGRAMMING_EXERCISE + '/' + irisExercise.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        afterEach(() => {
            httpMock.verify();
        });
    });
});
