import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { irisExercise, mockClientMessage, mockConversation, mockServerMessage } from 'test/helpers/sample/iris-sample-data';
import { IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { provideHttpClient } from '@angular/common/http';

describe('IrisChatHttpService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisChatHttpService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisChatHttpService],
        });
        service = TestBed.inject(IrisChatHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create a message', async () => {
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
        });

        it('should resend a message', async () => {
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
        });

        it('should return all messages for a session', async () => {
            const returnedFromService = [mockServerMessage, mockClientMessage];
            const expected = returnedFromService;
            service
                .getMessages(mockConversation.id)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
        });

        it('should update message helpful field', async () => {
            const returnedFromService = { ...mockServerMessage, helpful: true };
            const expected = returnedFromService;
            service
                .rateMessage(mockConversation.id, mockServerMessage.id, true)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
        });

        it('should create a session', async () => {
            const returnedFromService = { id: '1' };
            service
                .createSession(ChatServiceMode.COURSE + '/' + 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(returnedFromService));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService, { status: 201, statusText: 'Created' });
        });

        it('should return current session', async () => {
            const returnedFromService = mockConversation;
            const expected = returnedFromService;
            service
                .getCurrentSessionOrCreateIfNotExists(ChatServiceMode.PROGRAMMING_EXERCISE + '/' + irisExercise.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
