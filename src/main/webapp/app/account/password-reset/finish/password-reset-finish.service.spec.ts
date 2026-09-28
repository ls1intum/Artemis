/**
 * Vitest tests for PasswordResetFinishService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { PasswordResetFinishService } from 'app/account/password-reset/finish/password-reset-finish.service';
import { HttpClient } from '@angular/common/http';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('PasswordResetFinishService', () => {
    let service: PasswordResetFinishService;
    let httpClient: HttpClient;
    let postSpy: ReturnType<typeof vi.spyOn>;

    const postURL = 'api/core/public/account/reset-password/finish';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        }).compileComponents();

        service = TestBed.inject(PasswordResetFinishService);
        httpClient = TestBed.inject(HttpClient);
        postSpy = vi.spyOn(httpClient, 'post');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send a POST request to reset password with key and newPassword', () => {
        const resetKeyId = 'reset-key-123';
        const resetKeySecret = 'reset-key-123-secret';
        const newPassword = 'newSecurePassword123';

        service.completePasswordReset(resetKeyId, resetKeySecret, newPassword).subscribe();

        expect(postSpy).toHaveBeenCalledOnce();
        expect(postSpy).toHaveBeenCalledWith(postURL, { keyId: resetKeyId, keySecret: resetKeySecret, newPassword });
    });
});
