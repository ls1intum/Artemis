import { afterEach, beforeEach, describe, expect, expectTypeOf, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PushNotificationApi } from 'app/openapi/api/push-notification-api';
import { PushNotificationRegisterBody } from 'app/openapi/model/push-notification-register-body';
import { PushNotificationRegister } from 'app/openapi/model/push-notification-register';
import { PushNotificationUnregisterRequest } from 'app/openapi/model/push-notification-unregister-request';

/** No production Angular push caller exists; these tests exercise only the generated native-facing HTTP contract. */
describe('Generated push notification transport', () => {
    let api: PushNotificationApi;
    let http: HttpTestingController;
    const registerUrl = '/api/notification/push_notification/register';
    const unregisterUrl = '/api/notification/push_notification/unregister';
    const fakeToken = 'fake+/= token π';

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        api = TestBed.inject(PushNotificationApi);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it.each(['FIREBASE', 'APNS'] as const)('posts %s JSON and returns the encryption response body', (deviceType) => {
        const input: PushNotificationRegisterBody = { token: fakeToken, deviceType, apiType: 'IOS_V2', versionCode: '1.2.3' };
        const response: PushNotificationRegister = { secretKey: 'ZmFrZS1rZXktZm9yLWNvbnRyYWN0LW9ubHk=', algorithm: 'AES/CBC/PKCS5Padding' };
        let received: PushNotificationRegister | undefined;
        api.registerPushNotificationDevice(input).subscribe((body) => (received = body));
        const request = http.expectOne(registerUrl);
        expect(request.request.method).toBe('POST');
        expect(request.request.serializeBody()).toBe(JSON.stringify(input));
        expect(request.request.detectContentTypeHeader()).toBe('application/json');
        expect(request.request.body).toBe(input);
        request.flush(response, { status: 200, statusText: 'OK' });
        expect(received).toEqual(response);
        expect(input.token).toBe(fakeToken);
    });

    it('leaves omitted defaults to the server and preserves explicit nulls instead of inventing values', () => {
        const omitted: PushNotificationRegisterBody = { token: fakeToken, deviceType: 'FIREBASE' };
        api.registerPushNotificationDevice(omitted).subscribe();
        const first = http.expectOne(registerUrl);
        expect(first.request.serializeBody()).toBe(JSON.stringify(omitted));
        first.flush({ secretKey: 'fake', algorithm: 'AES/CBC/PKCS5Padding' });

        const explicitNull: PushNotificationRegisterBody = { token: fakeToken, deviceType: 'APNS', apiType: null, versionCode: null };
        api.registerPushNotificationDevice(explicitNull).subscribe();
        const second = http.expectOne(registerUrl);
        expect(second.request.serializeBody()).toBe(JSON.stringify(explicitNull));
        second.flush({ secretKey: 'fake', algorithm: 'AES/CBC/PKCS5Padding' });
        expectTypeOf<PushNotificationRegisterBody['apiType']>().toEqualTypeOf<'DEFAULT' | 'IOS_V2' | null | undefined>();
    });

    it.each(['FIREBASE', 'APNS'] as const)('encodes a %s query token once and sends no invented body', (deviceType) => {
        let completed = false;
        api.unregisterPushNotificationDevice(fakeToken, deviceType).subscribe({ complete: () => (completed = true) });
        const request = http.expectOne(`${unregisterUrl}?token=fake%2B%2F%3D+token+%CF%80&deviceType=${deviceType}`);
        expect(request.request.method).toBe('DELETE');
        const url = new URL(request.request.urlWithParams, 'https://fake.invalid');
        expect(url.searchParams.get('token')).toBe(fakeToken);
        expect(url.searchParams.get('deviceType')).toBe(deviceType);
        expect(request.request.urlWithParams).not.toContain('%252B');
        expect(request.request.body).toBeNull();
        expect(request.request.serializeBody()).toBeNull();
        request.flush(null, { status: 200, statusText: 'OK' });
        expect(completed).toBe(true);
    });

    it('preserves the optional deprecated DELETE body through HttpClient options', () => {
        const body: PushNotificationUnregisterRequest = { token: fakeToken, deviceType: 'APNS' };
        let response: void | undefined;
        let completed = false;
        api.unregisterPushNotificationDevice(undefined, undefined, body).subscribe({ next: (value) => (response = value), complete: () => (completed = true) });
        const request = http.expectOne(unregisterUrl);
        expect(request.request.method).toBe('DELETE');
        expect(request.request.body).toBe(body);
        expect(request.request.serializeBody()).toBe(JSON.stringify(body));
        request.flush(null, { status: 200, statusText: 'OK' });
        // Angular returns null for an empty JSON response even though the generated public type is void.
        expect(response).toBeNull();
        expect(completed).toBe(true);
    });

    it('transports conflicting and partial sources without pre-merging or dropping blank query values', () => {
        const body: PushNotificationUnregisterRequest = { token: 'fake-body', deviceType: 'APNS' };
        api.unregisterPushNotificationDevice('fake-query', 'FIREBASE', body).subscribe();
        const conflict = http.expectOne(`${unregisterUrl}?token=fake-query&deviceType=FIREBASE`);
        expect(conflict.request.body).toBe(body);
        conflict.flush(null);

        api.unregisterPushNotificationDevice(undefined, 'FIREBASE', body).subscribe();
        const mixed = http.expectOne(`${unregisterUrl}?deviceType=FIREBASE`);
        expect(mixed.request.body).toBe(body);
        mixed.flush(null);

        api.unregisterPushNotificationDevice('', undefined, body).subscribe();
        const blank = http.expectOne(`${unregisterUrl}?token=`);
        expect(blank.request.body).toBe(body);
        blank.flush(null);
    });

    it('allows a no-argument DELETE and forwards incomplete/ownership errors from the server', () => {
        let received: HttpErrorResponse | undefined;
        api.unregisterPushNotificationDevice().subscribe({ error: (error: HttpErrorResponse) => (received = error) });
        const incomplete = http.expectOne(unregisterUrl);
        expect(incomplete.request.body).toBeNull();
        incomplete.flush(null, { status: 400, statusText: 'Bad Request' });
        expect(received?.status).toBe(400);

        api.unregisterPushNotificationDevice('fake-missing', 'APNS').subscribe({ error: (error: HttpErrorResponse) => (received = error) });
        http.expectOne(`${unregisterUrl}?token=fake-missing&deviceType=APNS`).flush(null, { status: 404, statusText: 'Not Found' });
        expect(received?.status).toBe(404);
    });
});
