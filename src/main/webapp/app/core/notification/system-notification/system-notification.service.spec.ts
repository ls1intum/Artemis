import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { SystemNotification as SystemNotificationDto } from 'app/openapi/model/system-notification';
import dayjs from 'dayjs/esm';

describe('SystemNotificationService generated HTTP boundary', () => {
    let service: SystemNotificationService;
    let httpMock: HttpTestingController;
    const wire: SystemNotificationDto = {
        id: 1,
        title: 'DST maintenance',
        type: 'WARNING',
        notificationDate: '2026-10-25T02:30:00+02:00',
        expireDate: '2026-10-25T02:30:00+01:00',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(SystemNotificationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('finds a body model with the same instants without mutating the wire DTO', () => {
        let result: SystemNotification | undefined;
        service.find(1).subscribe((notification) => (result = notification));
        httpMock.expectOne({ method: 'GET', url: '/api/notification/admin/system-notifications/1' }).flush(wire);
        expect(result?.id).toBe(1);
        expect(dayjs.isDayjs(result?.notificationDate)).toBe(true);
        expect(result?.notificationDate?.toISOString()).toBe('2026-10-25T00:30:00.000Z');
        expect(result?.expireDate?.toISOString()).toBe('2026-10-25T01:30:00.000Z');
        expect(wire.notificationDate).toBe('2026-10-25T02:30:00+02:00');
        expect(wire.expireDate).toBe('2026-10-25T02:30:00+01:00');
    });

    it('propagates a missing notification error instead of manufacturing a response', () => {
        let error: HttpErrorResponse | undefined;
        service.find(99).subscribe({ error: (response: HttpErrorResponse) => (error = response) });
        httpMock.expectOne('/api/notification/admin/system-notifications/99').flush(null, { status: 404, statusText: 'Not Found' });
        expect(error?.status).toBe(404);
    });

    it('propagates forbidden admin reads without falling back to a public endpoint', () => {
        let listError: HttpErrorResponse | undefined;
        let detailError: HttpErrorResponse | undefined;
        service.query().subscribe({ error: (error: HttpErrorResponse) => (listError = error) });
        service.find(1).subscribe({ error: (error: HttpErrorResponse) => (detailError = error) });
        httpMock.expectOne('/api/notification/admin/system-notifications').flush(null, { status: 403, statusText: 'Forbidden' });
        httpMock.expectOne('/api/notification/admin/system-notifications/1').flush(null, { status: 403, statusText: 'Forbidden' });
        expect(listError?.status).toBe(403);
        expect(detailError?.status).toBe(403);
        httpMock.expectNone('/api/notification/public/system-notifications/active');
    });

    it('preserves ordered repeated sorts, page zero, pagination headers and response metadata', () => {
        let result: HttpResponse<SystemNotification[]> | undefined;
        const link = '</api/notification/admin/system-notifications?page=1&size=10>; rel="next"';
        service.query({ page: 0, size: 10, sort: ['notificationDate,desc', 'id,asc'] }).subscribe((response) => (result = response));
        const request = httpMock.expectOne((req) => req.method === 'GET');
        const url = new URL(request.request.urlWithParams, 'https://artemis.example');
        expect(url.pathname).toBe('/api/notification/admin/system-notifications');
        expect(url.searchParams.get('page')).toBe('0');
        expect(url.searchParams.get('size')).toBe('10');
        expect(url.searchParams.getAll('sort')).toEqual(['notificationDate,desc', 'id,asc']);
        expect(request.request.urlWithParams).toContain('sort=notificationDate%2Cdesc&sort=id%2Casc');
        request.flush([wire], { status: 200, statusText: 'OK', headers: { Link: link, 'X-Total-Count': '21' } });
        expect(result?.body?.[0].notificationDate?.toISOString()).toBe('2026-10-25T00:30:00.000Z');
        expect(result?.headers.get('Link')).toBe(link);
        expect(result?.headers.get('X-Total-Count')).toBe('21');
        expect(result?.status).toBe(200);
        expect(result?.url).toBe(request.request.urlWithParams);
    });

    it('leaves omitted paging options to the server and preserves an empty page and total zero', () => {
        let result: HttpResponse<SystemNotification[]> | undefined;
        service.query().subscribe((response) => (result = response));
        httpMock.expectOne('/api/notification/admin/system-notifications').flush([], { headers: { 'X-Total-Count': '0' } });
        expect(result?.body).toEqual([]);
        expect(result?.headers.get('X-Total-Count')).toBe('0');
    });

    it('preserves a null list body', () => {
        let result: HttpResponse<SystemNotification[]> | undefined;
        service.query().subscribe((response) => (result = response));
        httpMock.expectOne('/api/notification/admin/system-notifications').flush(null);
        expect(result?.body).toBeNull();
    });

    it('loads the public list without credentials and keeps absent dates absent', () => {
        let result: SystemNotification[] | undefined;
        service.getActiveNotifications().subscribe((notifications) => (result = notifications));
        const request = httpMock.expectOne({ method: 'GET', url: '/api/notification/public/system-notifications/active' });
        expect(request.request.headers.has('Authorization')).toBe(false);
        request.flush([{ id: 2 }, wire]);
        expect(result?.[0].notificationDate).toBeUndefined();
        expect(result?.[0].expireDate).toBeUndefined();
        expect(result?.[1].expireDate?.toISOString()).toBe('2026-10-25T01:30:00.000Z');
    });

    it.each([[], null])('retains the public empty-list fallback for %s', (body) => {
        let result: SystemNotification[] | undefined;
        service.getActiveNotifications().subscribe((notifications) => (result = notifications));
        httpMock.expectOne('/api/notification/public/system-notifications/active').flush(body);
        expect(result).toEqual([]);
    });
});
