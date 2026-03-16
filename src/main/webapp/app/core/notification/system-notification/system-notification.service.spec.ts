import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

describe('SystemNotificationService', () => {
    setupTestBed({ zoneless: true });

    let service: SystemNotificationService;
    let httpMock: HttpTestingController;
    let elemDefault: SystemNotification;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        expectedResult = {} as HttpResponse<SystemNotification>;
        service = TestBed.inject(SystemNotificationService);
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

    describe('find', () => {
        it('should find a SystemNotification by id', async () => {
            const returnedFromService = {
                ...elemDefault,
                notificationDate: elemDefault.notificationDate!.toISOString(),
                expireDate: elemDefault.expireDate!.toISOString(),
            };

            service
                .find(1)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/communication/system-notifications/1' });
            req.flush(returnedFromService);

            expect(expectedResult.body).toBeDefined();
            expect(expectedResult.body.id).toBe(1);
            expect(expectedResult.body.title).toBe('Test Notification');
        });

        it('should handle null body in find response', async () => {
            service
                .find(1)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/communication/system-notifications/1' });
            req.flush(null);

            expect(expectedResult.body).toBeNull();
        });
    });

    describe('query', () => {
        it('should query SystemNotifications without parameters', async () => {
            const returnedFromService = [
                {
                    ...elemDefault,
                    notificationDate: elemDefault.notificationDate!.toISOString(),
                    expireDate: elemDefault.expireDate!.toISOString(),
                },
            ];

            service
                .query()
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe('api/communication/system-notifications');
            req.flush(returnedFromService);

            expect(expectedResult.body).toHaveLength(1);
            expect(expectedResult.body[0].id).toBe(1);
        });

        it('should query SystemNotifications with request parameters', async () => {
            const returnedFromService = [
                {
                    ...elemDefault,
                    notificationDate: elemDefault.notificationDate!.toISOString(),
                    expireDate: elemDefault.expireDate!.toISOString(),
                },
            ];

            service
                .query({ page: 0, size: 10, sort: ['id,asc'] })
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.params.get('page')).toBe('0');
            expect(req.request.params.get('size')).toBe('10');
            expect(req.request.params.getAll('sort')).toEqual(['id,asc']);
            req.flush(returnedFromService);

            expect(expectedResult.body).toHaveLength(1);
        });

        it('should handle null body in query response', async () => {
            service
                .query()
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(null);

            expect(expectedResult.body).toBeNull();
        });
    });

    describe('getActiveNotifications', () => {
        it('should get active notifications', async () => {
            const returnedFromService = [
                {
                    ...elemDefault,
                    notificationDate: elemDefault.notificationDate!.toISOString(),
                    expireDate: elemDefault.expireDate!.toISOString(),
                },
            ];

            let result: SystemNotification[] | undefined;
            service
                .getActiveNotifications()
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/core/public/system-notifications/active' });
            req.flush(returnedFromService);

            expect(result).toHaveLength(1);
            expect(result![0].id).toBe(1);
        });

        it('should return empty array when body is null', async () => {
            let result: SystemNotification[] | undefined;
            service
                .getActiveNotifications()
                .pipe(take(1))
                .subscribe((resp) => (result = resp));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/core/public/system-notifications/active' });
            req.flush(null);

            expect(result).toEqual([]);
        });
    });

    describe('convertSystemNotificationDatesFromClient', () => {
        it('should convert notification dates from client format', () => {
            const notification = new SystemNotification();
            notification.id = 1;
            // Use UTC times to avoid timezone issues
            notification.notificationDate = dayjs.utc('2023-01-15T10:00:00Z');
            notification.expireDate = dayjs.utc('2023-01-15T12:00:00Z');

            const result = service.convertSystemNotificationDatesFromClient(notification);

            expect(result.id).toBe(1);
            expect(result.notificationDate).toBe('2023-01-15T10:00:00.000Z');
            expect(result.expireDate).toBe('2023-01-15T12:00:00.000Z');
        });

        it('should handle undefined dates', () => {
            const notification = new SystemNotification();
            notification.id = 1;

            const result = service.convertSystemNotificationDatesFromClient(notification);

            expect(result.id).toBe(1);
            expect(result.notificationDate).toBeUndefined();
            expect(result.expireDate).toBeUndefined();
        });
    });

    describe('convertSystemNotificationResponseDatesFromServer', () => {
        it('should convert response dates from server format', () => {
            const notification = {
                id: 1,
                notificationDate: '2023-01-15T10:00:00Z',
                expireDate: '2023-01-15T12:00:00Z',
            } as any as SystemNotification;

            const response = new HttpResponse<SystemNotification>({ body: notification });
            const result = service.convertSystemNotificationResponseDatesFromServer(response);

            expect(result.body!.id).toBe(1);
            expect(dayjs.isDayjs(result.body!.notificationDate)).toBe(true);
            expect(dayjs.isDayjs(result.body!.expireDate)).toBe(true);
        });

        it('should handle null body in response', () => {
            const response = new HttpResponse<SystemNotification>({ body: null });
            const result = service.convertSystemNotificationResponseDatesFromServer(response);

            expect(result.body).toBeNull();
        });
    });

    describe('convertSystemNotificationArrayResponseDatesFromServer', () => {
        it('should convert array response dates from server format', () => {
            const notifications = [
                {
                    id: 1,
                    notificationDate: '2023-01-15T10:00:00Z',
                    expireDate: '2023-01-15T12:00:00Z',
                } as any as SystemNotification,
                {
                    id: 2,
                    notificationDate: '2023-01-16T10:00:00Z',
                    expireDate: '2023-01-16T12:00:00Z',
                } as any as SystemNotification,
            ];

            const response = new HttpResponse<SystemNotification[]>({ body: notifications });
            const result = service.convertSystemNotificationArrayResponseDatesFromServer(response);

            expect(result.body).toHaveLength(2);
            expect(dayjs.isDayjs(result.body![0].notificationDate)).toBe(true);
            expect(dayjs.isDayjs(result.body![0].expireDate)).toBe(true);
            expect(dayjs.isDayjs(result.body![1].notificationDate)).toBe(true);
            expect(dayjs.isDayjs(result.body![1].expireDate)).toBe(true);
        });

        it('should handle null body in array response', () => {
            const response = new HttpResponse<SystemNotification[]>({ body: null });
            const result = service.convertSystemNotificationArrayResponseDatesFromServer(response);

            expect(result.body).toBeNull();
        });

        it('should handle empty array in response', () => {
            const response = new HttpResponse<SystemNotification[]>({ body: [] });
            const result = service.convertSystemNotificationArrayResponseDatesFromServer(response);

            expect(result.body).toHaveLength(0);
        });
    });
});
