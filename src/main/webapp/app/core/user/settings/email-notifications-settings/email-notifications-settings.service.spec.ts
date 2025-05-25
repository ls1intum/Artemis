import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { EmailNotificationSettingsService } from './email-notifications-settings.service';

describe('EmailNotificationSettingsService', () => {
    let service: EmailNotificationSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [EmailNotificationSettingsService],
        });

        service = TestBed.inject(EmailNotificationSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
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

        const req = httpMock.expectOne('api/communication/email-notification-settings');
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);
    });

    it('should update a notification setting', () => {
        const type = 'NEW_LOGIN';
        const enabled = false;

        service.update(type, enabled).subscribe((response) => {
            expect(response).toBeTruthy();
        });

        const req = httpMock.expectOne(`api/communication/email-notification-settings/${type}`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ enabled });
        req.flush({});
    });
});
