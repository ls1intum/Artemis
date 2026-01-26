import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

describe('AdminSystemNotificationService', () => {
    setupTestBed({ zoneless: true });

    let service: AdminSystemNotificationService;
    let systemNotificationService: SystemNotificationService;
    let httpMock: HttpTestingController;
    let elemDefault: SystemNotification;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        expectedResult = {} as HttpResponse<SystemNotification>;
        service = TestBed.inject(AdminSystemNotificationService);
        systemNotificationService = TestBed.inject(SystemNotificationService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new SystemNotification();
        elemDefault.id = 1;
        elemDefault.title = 'Test Notification';
        elemDefault.text = 'Test notification text';
        elemDefault.type = SystemNotificationType.WARNING;
        elemDefault.notificationDate = dayjs().subtract(1, 'hour');
        elemDefault.expireDate = dayjs().add(1, 'hour');
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('create', () => {
        it('should create a SystemNotification', async () => {
            const newNotification = new SystemNotification();
            newNotification.title = 'New Notification';
            newNotification.text = 'New notification text';
            newNotification.type = SystemNotificationType.INFO;
            newNotification.notificationDate = dayjs('2023-01-15T10:00:00');
            newNotification.expireDate = dayjs('2023-01-15T12:00:00');

            const returnedFromService = {
                ...newNotification,
                id: 1,
                notificationDate: '2023-01-15T10:00:00.000Z',
                expireDate: '2023-01-15T12:00:00.000Z',
            };

            const convertDatesFromClientSpy = vi.spyOn(systemNotificationService, 'convertSystemNotificationDatesFromClient');
            const convertDatesFromServerSpy = vi.spyOn(systemNotificationService, 'convertSystemNotificationResponseDatesFromServer');

            service
                .create(newNotification)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'POST', url: 'api/communication/admin/system-notifications' });
            req.flush(returnedFromService);

            expect(convertDatesFromClientSpy).toHaveBeenCalledOnce();
            expect(convertDatesFromClientSpy).toHaveBeenCalledWith(newNotification);
            expect(convertDatesFromServerSpy).toHaveBeenCalledOnce();
            expect(expectedResult.body).toBeDefined();
            expect(expectedResult.body.id).toBe(1);
            expect(expectedResult.body.title).toBe('New Notification');
        });

        it('should send the converted notification to server', async () => {
            const newNotification = new SystemNotification();
            newNotification.title = 'New Notification';
            // Use UTC times to avoid timezone issues
            newNotification.notificationDate = dayjs.utc('2023-01-15T10:00:00Z');
            newNotification.expireDate = dayjs.utc('2023-01-15T12:00:00Z');

            const returnedFromService = {
                ...newNotification,
                id: 1,
                notificationDate: '2023-01-15T10:00:00.000Z',
                expireDate: '2023-01-15T12:00:00.000Z',
            };

            service
                .create(newNotification)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'POST' });
            // Verify the request body has converted dates
            expect(req.request.body.notificationDate).toBe('2023-01-15T10:00:00.000Z');
            expect(req.request.body.expireDate).toBe('2023-01-15T12:00:00.000Z');
            req.flush(returnedFromService);
        });
    });

    describe('update', () => {
        it('should update a SystemNotification', async () => {
            const updatedNotification = { ...elemDefault };
            updatedNotification.title = 'Updated Notification';

            const returnedFromService = {
                ...updatedNotification,
                notificationDate: updatedNotification.notificationDate!.toISOString(),
                expireDate: updatedNotification.expireDate!.toISOString(),
            };

            const convertDatesFromClientSpy = vi.spyOn(systemNotificationService, 'convertSystemNotificationDatesFromClient');
            const convertDatesFromServerSpy = vi.spyOn(systemNotificationService, 'convertSystemNotificationResponseDatesFromServer');

            service
                .update(updatedNotification)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/communication/admin/system-notifications' });
            req.flush(returnedFromService);

            expect(convertDatesFromClientSpy).toHaveBeenCalledOnce();
            expect(convertDatesFromClientSpy).toHaveBeenCalledWith(updatedNotification);
            expect(convertDatesFromServerSpy).toHaveBeenCalledOnce();
            expect(expectedResult.body).toBeDefined();
            expect(expectedResult.body.title).toBe('Updated Notification');
        });

        it('should send the converted notification to server on update', async () => {
            const updatedNotification = new SystemNotification();
            updatedNotification.id = 1;
            updatedNotification.title = 'Updated Notification';
            // Use UTC times to avoid timezone issues
            updatedNotification.notificationDate = dayjs.utc('2023-02-20T14:00:00Z');
            updatedNotification.expireDate = dayjs.utc('2023-02-20T16:00:00Z');

            const returnedFromService = {
                ...updatedNotification,
                notificationDate: '2023-02-20T14:00:00.000Z',
                expireDate: '2023-02-20T16:00:00.000Z',
            };

            service
                .update(updatedNotification)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'PUT' });
            // Verify the request body has converted dates
            expect(req.request.body.notificationDate).toBe('2023-02-20T14:00:00.000Z');
            expect(req.request.body.expireDate).toBe('2023-02-20T16:00:00.000Z');
            req.flush(returnedFromService);
        });
    });

    describe('delete', () => {
        it('should delete a SystemNotification', async () => {
            let result: HttpResponse<void> | undefined;

            service
                .delete(1)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'DELETE', url: 'api/communication/admin/system-notifications/1' });
            req.flush(null);

            expect(result).toBeDefined();
            expect(result!.ok).toBe(true);
        });

        it('should delete a SystemNotification with different id', async () => {
            let result: HttpResponse<void> | undefined;

            service
                .delete(42)
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'DELETE', url: 'api/communication/admin/system-notifications/42' });
            req.flush(null);

            expect(result).toBeDefined();
        });
    });

    describe('resourceUrl', () => {
        it('should have the correct resource URL', () => {
            expect(service.resourceUrl).toBe('api/communication/admin/system-notifications');
        });
    });
});
