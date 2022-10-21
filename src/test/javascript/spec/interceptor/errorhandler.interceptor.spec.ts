import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { throwError } from 'rxjs';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { EventManager } from 'app/core/util/event-manager.service';

describe(`ErrorHandlerInterceptor`, () => {
    let errorHandlerInterceptor: ErrorHandlerInterceptor;

    let eventManagerMock: EventManager;

    beforeEach(() => {
        eventManagerMock = {
            broadcast: jest.fn(),
        } as any as EventManager;

        errorHandlerInterceptor = new ErrorHandlerInterceptor(eventManagerMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([400, 401, 403, 404, 500])('should broadcast http errors', (status: number) => {
        const error = new HttpErrorResponse({ status });
        const mockHandler = {
            handle: () => throwError(() => error),
        };

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(eventManagerMock.broadcast).toHaveBeenCalledOnce();
        expect(eventManagerMock.broadcast).toHaveBeenCalledWith({
            name: 'artemisApp.httpError',
            content: error,
        });
    });

    it('should not broadcast an http error if status is 401 and message is empty', () => {
        const error = new HttpErrorResponse({ status: 401 });
        // @ts-ignore
        error.message = '';
        const mockHandler = {
            handle: () => throwError(() => error),
        };

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(eventManagerMock.broadcast).not.toHaveBeenCalled();
    });

    it('should not broadcast an http error if status is 401 but url includes /api/account', () => {
        const error = new HttpErrorResponse({ status: 401, url: '/api/account' });
        const mockHandler = {
            handle: () => throwError(() => error),
        };

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(eventManagerMock.broadcast).not.toHaveBeenCalled();
    });
});
