import { HttpHandler, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';

describe(`NotificationInterceptor`, () => {
    let notificationInterceptor: NotificationInterceptor;

    let alertServiceMock: AlertService;

    beforeEach(() => {
        alertServiceMock = {
            success: jest.fn(),
        } as any as AlertService;

        notificationInterceptor = new NotificationInterceptor(alertServiceMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create a success alert if alert headers are given', () => {
        const requestMock = new HttpRequest('GET', `${SERVER_API_URL}test`);
        const mockHandler = {
            handle: () =>
                of(
                    new HttpResponse({
                        headers: new HttpHeaders({
                            'X-app-alert': 'Test',
                            'X-app-params': 'ABC',
                        }),
                    }),
                ),
        } as any as HttpHandler;

        notificationInterceptor.intercept(requestMock, mockHandler).subscribe();

        expect(alertServiceMock.success).toHaveBeenCalledOnce();
        expect(alertServiceMock.success).toHaveBeenCalledWith('Test', { param: 'ABC' });
    });

    it('should not spawn an alert if headers are missing', () => {
        const requestMock = new HttpRequest('GET', `${SERVER_API_URL}test`);
        const mockHandler = {
            handle: () =>
                of(
                    new HttpResponse({
                        headers: new HttpHeaders({}),
                    }),
                ),
        } as any as HttpHandler;

        notificationInterceptor.intercept(requestMock, mockHandler).subscribe();

        expect(alertServiceMock.success).not.toHaveBeenCalled();
    });
});
