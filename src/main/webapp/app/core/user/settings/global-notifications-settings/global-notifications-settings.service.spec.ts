import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GlobalNotificationSettingsService } from './global-notifications-settings.service';
import { firstValueFrom } from 'rxjs';

describe('GlobalNotificationSettingsService', () => {
    setupTestBed({ zoneless: true });

    let service: GlobalNotificationSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [GlobalNotificationSettingsService, provideHttpClient(), provideHttpClientTesting()],
        });
        await TestBed.compileComponents();
        service = TestBed.inject(GlobalNotificationSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should fetch all notification settings', async () => {
        const mockSettings = {
            NEW_LOGIN: true,
            NEW_PASSKEY_ADDED: false,
            VCS_TOKEN_EXPIRED: true,
            SSH_KEY_EXPIRED: false,
        };

        const promise = firstValueFrom(service.getAll());

        const req = httpMock.expectOne('api/communication/global-notification-settings');
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);

        const settings = await promise;
        expect(settings).toEqual(mockSettings);
    });

    it('should update a notification setting', async () => {
        const type = 'NEW_LOGIN';
        const enabled = false;

        const promise = firstValueFrom(service.update(type, enabled));

        const req = httpMock.expectOne(`api/communication/global-notification-settings/${type}`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ enabled });
        req.flush({});

        const response = await promise;
        expect(response).toBeTruthy();
    });
});
