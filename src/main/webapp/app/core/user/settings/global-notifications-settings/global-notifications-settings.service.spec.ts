import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GlobalNotificationSettingsService } from './global-notifications-settings.service';

describe('GlobalNotificationSettingsService', () => {
    let service: GlobalNotificationSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [GlobalNotificationSettingsService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(GlobalNotificationSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should fetch all notification settings', () => {
        const mockSettings = {
            NEW_LOGIN: true,
            NEW_PASSKEY_ADDED: false,
            VCS_TOKEN_EXPIRED: true,
            SSH_KEY_EXPIRED: false,
        };

        service.getAll().subscribe((settings) => {
            expect(settings).toEqual(mockSettings);
        });

        const req = httpMock.expectOne('api/communication/global-notification-settings');
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);
    });

    it('should update a notification setting', () => {
        const type = 'NEW_LOGIN';
        const enabled = false;

        service.update(type, enabled).subscribe((response) => {
            expect(response).toBeTruthy();
        });

        const req = httpMock.expectOne(`api/communication/global-notification-settings/${type}`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ enabled });
        req.flush({});
    });
});
