import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserSettingsService } from './user-settings.service';
import { User } from 'app/account/user/user.model';

describe('UserSettingsService', () => {
    let service: UserSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), UserSettingsService],
        });
        service = TestBed.inject(UserSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('updateProfilePicture', () => {
        it('should upload profile picture', () => {
            const mockBlob = new Blob(['test'], { type: 'image/jpeg' });
            const mockUser: User = { id: 1, login: 'testuser' } as User;

            service.updateProfilePicture(mockBlob).subscribe((response) => {
                expect(response.body).toEqual(mockUser);
            });

            const req = httpMock.expectOne(service.profilePictureResourceUrl);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body instanceof FormData).toBe(true);
            req.flush(mockUser);
        });
    });

    describe('removeProfilePicture', () => {
        it('should delete profile picture', () => {
            const mockUser: User = { id: 1, login: 'testuser' } as User;

            service.removeProfilePicture().subscribe((response) => {
                expect(response.body).toEqual(mockUser);
            });

            const req = httpMock.expectOne(service.profilePictureResourceUrl);
            expect(req.request.method).toBe('DELETE');
            req.flush(mockUser);
        });
    });
});
