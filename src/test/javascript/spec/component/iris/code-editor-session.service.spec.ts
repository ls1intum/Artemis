import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ConversationErrorOccurredAction, SessionReceivedAction } from 'app/iris/state-store.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { of, throwError } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { mockClientMessage, mockServerMessage } from '../../helpers/sample/iris-sample-data';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';

/**
 * This has a lot of duplication with chat-session.service.spec.ts!
 * TODO: Refactor this to use a common base class
 */
describe('IrisCodeEditorSessionService', () => {
    let irisSessionService: IrisCodeEditorSessionService;
    let stateStore: IrisStateStore;
    let mockHttpSessionService: IrisHttpCodeEditorSessionService;
    let mockHttpMessageService: IrisHttpCodeEditorMessageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisStateStore, IrisHttpCodeEditorSessionService, IrisHttpCodeEditorMessageService, IrisCodeEditorSessionService],
        });
        stateStore = TestBed.inject(IrisStateStore);
        irisSessionService = TestBed.inject(IrisCodeEditorSessionService);
        mockHttpSessionService = TestBed.inject(IrisHttpCodeEditorSessionService);
        mockHttpMessageService = TestBed.inject(IrisHttpCodeEditorMessageService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('createNewSession', () => {
        it('should create a new session if success', async () => {
            // given
            const exerciseId = 123;
            const sessionId = 456;
            const createSessionMock = jest.spyOn(mockHttpSessionService, 'createSession').mockReturnValueOnce(of({ id: sessionId }));
            const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

            // when
            await irisSessionService.createNewSession(exerciseId);

            // then
            expect(createSessionMock).toHaveBeenCalledWith(exerciseId);
            expect(dispatchSpy).toHaveBeenCalledWith(new SessionReceivedAction(sessionId, []));
        });

        it('should dispatch error if fail', async () => {
            // given
            const exerciseId = 123;
            const createSessionMock = jest.spyOn(mockHttpSessionService, 'createSession').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
            const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

            // when
            await irisSessionService.createNewSession(exerciseId);

            // then
            expect(createSessionMock).toHaveBeenCalledWith(exerciseId);
            expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.SESSION_CREATION_FAILED));
        });
    });

    it('should create a new session if getCurrentSession returns 404 and createSession is successful', async () => {
        // given
        const exerciseId = 123;
        const sessionId = 456;
        const getCurrentSessionMock = jest.spyOn(mockHttpSessionService, 'getCurrentSession').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
        const createSessionMock = jest.spyOn(mockHttpSessionService, 'createSession').mockReturnValueOnce(of({ id: sessionId }));
        const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

        // when
        await irisSessionService.getCurrentSessionOrCreate(exerciseId);

        // then
        expect(getCurrentSessionMock).toHaveBeenCalledWith(exerciseId);
        expect(createSessionMock).toHaveBeenCalledWith(exerciseId);

        expect(dispatchSpy).toHaveBeenCalledWith(new SessionReceivedAction(sessionId, []));
    });

    it('should retrieve messages if getCurrentSession returns 200', async () => {
        // given
        const exerciseId = 123;
        const sessionId = 456;
        const getCurrentSessionMock = jest.spyOn(mockHttpSessionService, 'getCurrentSession').mockReturnValueOnce(of(new HttpResponse<IrisSession>({ body: { id: sessionId } })));
        const getMessagesMock = jest.spyOn(mockHttpMessageService, 'getMessages').mockReturnValueOnce(
            of(
                new HttpResponse<IrisMessage[]>({
                    status: 200,
                    body: [mockClientMessage, mockServerMessage],
                }),
            ),
        );
        const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

        // when
        await irisSessionService.getCurrentSessionOrCreate(exerciseId);

        // then
        expect(getCurrentSessionMock).toHaveBeenCalledWith(exerciseId);
        expect(getMessagesMock).toHaveBeenCalledWith(sessionId);

        expect(dispatchSpy).toHaveBeenCalledWith(new SessionReceivedAction(sessionId, expect.arrayContaining([mockClientMessage, mockServerMessage])));
    });

    it('should dispatch an error if getCurrentSession returns an error', async () => {
        // given
        const exerciseId = 123;
        jest.spyOn(mockHttpSessionService, 'getCurrentSession').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

        // when
        await irisSessionService.getCurrentSessionOrCreate(exerciseId);

        // then
        expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.SESSION_LOAD_FAILED));
    });

    it('should dispatch an error if getCurrentSession is successful. but getMessages returned an error', async () => {
        // given
        const exerciseId = 123;
        const sessionId = 456;
        const getCurrentSessionMock = jest.spyOn(mockHttpSessionService, 'getCurrentSession').mockReturnValueOnce(
            of(
                new HttpResponse<IrisSession>({
                    status: 200,
                    body: {
                        id: sessionId,
                    },
                }),
            ),
        );
        const getMessagesMock = jest.spyOn(mockHttpMessageService, 'getMessages').mockReturnValueOnce(
            throwError(
                new HttpResponse<IrisMessage[]>({
                    status: 500,
                }),
            ),
        );

        const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

        // when
        await irisSessionService.getCurrentSessionOrCreate(exerciseId);

        // then
        expect(getCurrentSessionMock).toHaveBeenCalledWith(exerciseId);
        expect(getMessagesMock).toHaveBeenCalledWith(sessionId);

        expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.HISTORY_LOAD_FAILED));
    });

    it('should dispatch an error if getCurrentSession is 404 and createSession returned an error', async () => {
        // given
        const exerciseId = 123;
        const getCurrentSessionMock = jest.spyOn(mockHttpSessionService, 'getCurrentSession').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
        const createSessionMock = jest.spyOn(mockHttpSessionService, 'createSession').mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
        const dispatchSpy = jest.spyOn(stateStore, 'dispatch');

        // when
        await irisSessionService.getCurrentSessionOrCreate(exerciseId);

        // then
        expect(getCurrentSessionMock).toHaveBeenCalledWith(exerciseId);
        expect(createSessionMock).toHaveBeenCalledWith(exerciseId);

        expect(dispatchSpy).toHaveBeenCalledWith(new ConversationErrorOccurredAction(IrisErrorMessageKey.SESSION_CREATION_FAILED));
    });
});
