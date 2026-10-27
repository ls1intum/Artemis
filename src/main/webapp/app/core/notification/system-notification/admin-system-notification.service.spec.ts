import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HTTP_INTERCEPTORS, HttpErrorResponse, HttpResponse, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';
import { SystemNotification, SystemNotificationType } from 'app/admin/system-notification-management/system-notification.model';
import { SystemNotification as SystemNotificationDto } from 'app/openapi/model/system-notification';
import { AdminSystemNotificationApi } from 'app/openapi/api/admin-system-notification-api';
import { SystemNotificationUpdate } from 'app/openapi/model/system-notification-update';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { AlertService } from 'app/foundation/service/alert.service';
import dayjs from 'dayjs/esm';

describe('AdminSystemNotificationService generated HTTP boundary', () => {
    let service: AdminSystemNotificationService;
    let httpMock: HttpTestingController;
    const success = vi.fn();
    const wire: SystemNotificationDto = {
        id: 42,
        title: 'Maintenance',
        type: 'INFO',
        notificationDate: '2026-10-25T00:30:00Z',
        expireDate: '2026-10-25T01:30:00Z',
    };
    const notification = (): SystemNotification => ({
        title: 'Maintenance',
        type: SystemNotificationType.INFO,
        notificationDate: dayjs('2026-10-25T02:30:00+02:00'),
        expireDate: dayjs('2026-10-25T02:30:00+01:00'),
    });

    beforeEach(() => {
        success.mockClear();
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                { provide: HTTP_INTERCEPTORS, useClass: NotificationInterceptor, multi: true },
                { provide: AlertService, useValue: { success } },
            ],
        });
        service = TestBed.inject(AdminSystemNotificationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it.each([undefined, false, true])('creates with maintenance email selection %s and preserves 201, Location and configured alerts', (email) => {
        const model = notification();
        let result: HttpResponse<SystemNotification> | undefined;
        service.create(model, email).subscribe((response) => (result = response));
        const request = httpMock.expectOne((req) => req.method === 'POST');
        const url = new URL(request.request.urlWithParams, 'https://artemis.example');
        expect(url.pathname).toBe('/api/notification/admin/system-notifications');
        expect(url.searchParams.get('sendMaintenanceEmail')).toBe(email ? 'true' : null);
        expect(request.request.body).toEqual({
            id: undefined,
            title: 'Maintenance',
            text: undefined,
            type: 'INFO',
            notificationDate: '2026-10-25T00:30:00.000Z',
            expireDate: '2026-10-25T01:30:00.000Z',
        });
        request.flush(wire, {
            status: 201,
            statusText: 'Created',
            headers: { Location: '/api/notifications/42', 'X-customApp-alert': 'customApp.systemNotification.created', 'X-customApp-params': '42' },
        });
        expect(result?.status).toBe(201);
        expect(result?.url).toBe(request.request.urlWithParams);
        expect(result?.headers.get('Location')).toBe('/api/notifications/42');
        expect(result?.headers.get('X-customApp-alert')).toBe('customApp.systemNotification.created');
        expect(result?.body?.notificationDate?.valueOf()).toBe(model.notificationDate?.valueOf());
        expect(result?.body?.expireDate?.valueOf()).toBe(model.expireDate?.valueOf());
        expect(dayjs.isDayjs(model.notificationDate)).toBe(true);
        expect(wire.notificationDate).toBe('2026-10-25T00:30:00Z');
        expect(success).toHaveBeenCalledWith('customApp.systemNotification.created', { param: '42' });
    });

    it('updates an explicit write DTO and retains response dates and update headers', () => {
        const model = { ...notification(), id: 42 };
        let result: HttpResponse<SystemNotification> | undefined;
        service.update(model).subscribe((response) => (result = response));
        const request = httpMock.expectOne({ method: 'PUT', url: '/api/notification/admin/system-notifications' });
        expect(request.request.body.id).toBe(42);
        expect(request.request.body.notificationDate).toBe('2026-10-25T00:30:00.000Z');
        expect(request.request.body.expireDate).toBe('2026-10-25T01:30:00.000Z');
        expect(request.request.body.author).toBeUndefined();
        request.flush(wire, { headers: { 'X-artemisApp-alert': 'artemisApp.systemNotification.updated', 'X-artemisApp-params': '42' } });
        expect(result?.body?.id).toBe(42);
        expect(result?.body?.expireDate?.valueOf()).toBe(model.expireDate?.valueOf());
        expect(result?.headers.get('X-artemisApp-alert')).toBe('artemisApp.systemNotification.updated');
        expect(success).toHaveBeenCalledWith('artemisApp.systemNotification.updated', { param: '42' });
    });

    it('keeps explicit nullable replacement fields in the generated request contract', () => {
        const input: SystemNotificationUpdate = {
            id: 42,
            title: null,
            text: null,
            type: null,
            notificationDate: '2026-10-25T00:30:00Z',
            expireDate: '2026-10-25T01:30:00Z',
        };
        let result: HttpResponse<SystemNotificationDto> | undefined;
        TestBed.inject(AdminSystemNotificationApi)
            .updateSystemNotification(input)
            .subscribe((response) => (result = response));
        const request = httpMock.expectOne({ method: 'PUT', url: '/api/notification/admin/system-notifications' });
        expect(request.request.serializeBody()).toBe(JSON.stringify(input));
        request.flush({ id: 42, notificationDate: input.notificationDate, expireDate: input.expireDate });
        expect(result?.body?.id).toBe(42);
    });

    it('returns an empty 200 deletion response and delivers its alert through the real interceptor', () => {
        let result: HttpResponse<void> | undefined;
        service.delete(42).subscribe((response) => (result = response));
        httpMock.expectOne({ method: 'DELETE', url: '/api/notification/admin/system-notifications/42' }).flush(null, {
            status: 200,
            statusText: 'OK',
            headers: { 'X-artemisApp-alert': 'artemisApp.systemNotification.deleted', 'X-artemisApp-params': '42' },
        });
        expect(result?.body).toBeNull();
        expect(result?.status).toBe(200);
        expect(result?.headers.get('X-artemisApp-params')).toBe('42');
        expect(success).toHaveBeenCalledWith('artemisApp.systemNotification.deleted', { param: '42' });
    });

    it.each([0, 12])('returns the numeric maintenance recipient count %s', (count) => {
        let result: number | undefined;
        service.getMaintenanceRecipientsCount().subscribe((response) => (result = response));
        httpMock.expectOne({ method: 'GET', url: '/api/notification/admin/system-notifications/maintenance-recipients-count' }).flush(count);
        expect(result).toBe(count);
    });

    it('leaves ordered-date validation errors to the server and propagates the failed save', () => {
        const model = notification();
        model.expireDate = model.notificationDate;
        let result: HttpErrorResponse | undefined;
        service.update(model).subscribe({ error: (error: HttpErrorResponse) => (result = error) });
        httpMock
            .expectOne({ method: 'PUT', url: '/api/notification/admin/system-notifications' })
            .flush({ message: 'error.systemNotificationNeedsNotificationBeforeExpiration' }, { status: 400, statusText: 'Bad Request' });
        expect(result?.status).toBe(400);
        expect(success).not.toHaveBeenCalled();
    });

    it('reports an incomplete editor model through the subscription error channel without inventing dates', () => {
        let error: Error | undefined;
        service.create(new SystemNotification()).subscribe({ error: (result: Error) => (error = result) });
        expect(error?.message).toContain('requires both');
        httpMock.expectNone((request) => request.method === 'POST');
    });
});
