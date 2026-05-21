/**
 * Vitest tests for PasswordResetFinishService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { PasswordResetFinishService } from 'app/core/account/password-reset/finish/password-reset-finish.service';
import { HttpClient } from '@angular/common/http';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('PasswordResetFinishService', () => {
    setupTestBed({ zoneless: true });

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
        const resetKey = 'reset-key-123';
        const newPassword = 'newSecurePassword123';

        service.completePasswordReset(resetKey, newPassword).subscribe();

        expect(postSpy).toHaveBeenCalledOnce();
        expect(postSpy).toHaveBeenCalledWith(postURL, { key: resetKey, newPassword });
    });
});
