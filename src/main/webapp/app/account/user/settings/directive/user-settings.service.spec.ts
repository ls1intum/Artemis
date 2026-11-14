import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserSettingsService } from './user-settings.service';
import { User } from 'app/openapi/model/user';

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

    it('uploads a single JPEG file with the original bytes and lets the browser set the multipart boundary', async () => {
        const bytes = new Uint8Array([255, 216, 255, 224]);
        const blob = new Blob([bytes], { type: 'image/jpeg' });
        const user: User = { id: 1, login: 'testuser', imageUrl: 'profile.jpeg' };
        const next = vi.fn();

        service.updateProfilePicture(blob).subscribe({ next });

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/account/profile-picture' });
        expect(req.request.headers.has('Content-Type')).toBe(false);
        expect(req.request.body).toBeInstanceOf(FormData);
        const body: FormData = req.request.body;
        expect(Array.from(body.keys())).toEqual(['file']);
        const file = body.get('file');
        if (!(file instanceof File)) {
            throw new Error('Expected a multipart file');
        }
        expect(file.name).toBe('placeholderName.jpeg');
        expect(file.type).toBe('image/jpeg');
        const uploadedBytes = await new Promise<ArrayBuffer>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as ArrayBuffer);
            reader.onerror = () => reject(reader.error);
            reader.readAsArrayBuffer(file);
        });
        expect(new Uint8Array(uploadedBytes)).toEqual(bytes);

        req.flush(user);
        expect(next).toHaveBeenCalledExactlyOnceWith(user);
    });

    it('emits on a bodyless 200 when deleting the profile picture', () => {
        const next = vi.fn();
        service.removeProfilePicture().subscribe({ next });

        const req = httpMock.expectOne({ method: 'DELETE', url: '/api/account/profile-picture' });
        req.flush(null, { status: 200, statusText: 'OK' });

        expect(next).toHaveBeenCalledExactlyOnceWith(null);
    });
});
