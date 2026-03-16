import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpHandler, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { AlertService } from 'app/shared/service/alert.service';
import { TestBed } from '@angular/core/testing';

describe(`NotificationInterceptor`, () => {
    setupTestBed({ zoneless: true });

    let notificationInterceptor: NotificationInterceptor;

    let alertServiceMock: AlertService;

    beforeEach(() => {
        alertServiceMock = {
            success: vi.fn(),
        } as any as AlertService;

        TestBed.configureTestingModule({
            providers: [NotificationInterceptor, { provide: AlertService, useValue: alertServiceMock }],
        });

        notificationInterceptor = TestBed.inject(NotificationInterceptor);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create a success alert if alert headers are given', () => {
        const requestMock = new HttpRequest('GET', `test`);
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
        const requestMock = new HttpRequest('GET', `test`);
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
