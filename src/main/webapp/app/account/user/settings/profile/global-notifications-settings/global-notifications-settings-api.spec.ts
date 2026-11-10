import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { GlobalNotificationSettingApi } from 'app/openapi/api/global-notification-setting-api';
import { GlobalNotificationSetting, GlobalNotificationSettingNotificationTypeEnumValues } from 'app/openapi/model/global-notification-setting';
import { GLOBAL_NOTIFICATION_TYPES } from './global-notifications-settings.component';

describe('Global notification settings generated transport', () => {
    let api: GlobalNotificationSettingApi;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        api = TestBed.inject(GlobalNotificationSettingApi);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should read all notification flags without dropping false values', async () => {
        const settings = Object.fromEntries(GlobalNotificationSettingNotificationTypeEnumValues.map((type) => [type, type === 'NEW_LOGIN']));
        const response = firstValueFrom(api.getGlobalNotificationSettings());

        const request = httpMock.expectOne('/api/notification/global-notification-settings');
        expect(request.request.method).toBe('GET');
        expect(request.request.responseType).toBe('json');
        request.flush(settings);

        expect(await response).toEqual(settings);
        expect(Object.values(GLOBAL_NOTIFICATION_TYPES).sort()).toEqual([...GlobalNotificationSettingNotificationTypeEnumValues].sort());
    });

    it.each(GlobalNotificationSettingNotificationTypeEnumValues)('should send a false update and read the persisted DTO for %s', async (type) => {
        const setting: GlobalNotificationSetting = { id: 1, userId: 2, notificationType: type, enabled: false };
        const response = firstValueFrom(api.updateGlobalNotificationSetting(type, { enabled: false }));

        const request = httpMock.expectOne(`/api/notification/global-notification-settings/${type}`);
        expect(request.request.method).toBe('PUT');
        expect(request.request.body).toEqual({ enabled: false });
        expect(request.request.responseType).toBe('json');
        request.flush(setting);

        expect(await response).toEqual(setting);
    });

    it('should preserve HTTP failures for the caller', async () => {
        const response = firstValueFrom(api.getGlobalNotificationSettings());
        const failure = expect(response).rejects.toBeInstanceOf(HttpErrorResponse);

        httpMock.expectOne('/api/notification/global-notification-settings').flush('Forbidden', { status: 403, statusText: 'Forbidden' });

        await failure;
    });
});
