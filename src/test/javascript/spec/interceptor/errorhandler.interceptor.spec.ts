import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { throwError } from 'rxjs';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { EventManager } from 'app/core/util/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';

describe(`ErrorHandlerInterceptor`, () => {
    let errorHandlerInterceptor: ErrorHandlerInterceptor;

    let eventManagerMock: EventManager;
    let accountServiceMock: AccountService;

    beforeEach(() => {
        eventManagerMock = {
            broadcast: jest.fn(),
        } as any as EventManager;
        accountServiceMock = {
            isAuthenticated: jest.fn(),
        } as any as AccountService;

        errorHandlerInterceptor = new ErrorHandlerInterceptor(eventManagerMock, accountServiceMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([400, 401, 403, 404, 500])('should broadcast http errors', (status: number) => {
        const error = new HttpErrorResponse({ status });
        const mockHandler = {
            handle: () => throwError(() => error),
        };
        jest.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(eventManagerMock.broadcast).toHaveBeenCalledOnce();
        expect(eventManagerMock.broadcast).toHaveBeenCalledWith({
            name: 'artemisApp.httpError',
            content: error,
        });
    });

    it.each([{ url: '/api/public/account' }, { url: '/api/account' }])(
        'should not broadcast an http error if status is 401 but url includes /api/public/account or /api/account',
        ({ url }) => {
            const error = new HttpErrorResponse({ status: 401, url });
            const mockHandler = {
                handle: () => throwError(() => error),
            };
            const isAuthenticatedSpy = jest.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(false);

            errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

            expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        },
    );
});
