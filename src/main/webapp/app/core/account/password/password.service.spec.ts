/**
 * Vitest tests for PasswordService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { PasswordService } from 'app/core/account/password/password.service';
import { HttpClient } from '@angular/common/http';

describe('PasswordService', () => {
    setupTestBed({ zoneless: true });

    let passwordService: PasswordService;
    let httpService: HttpClient;
    let postStub: ReturnType<typeof vi.spyOn>;

    const postURL = 'api/core/account/change-password';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                passwordService = TestBed.inject(PasswordService);
                httpService = TestBed.inject(HttpClient);
                postStub = vi.spyOn(httpService, 'post');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set a new password for the current user', () => {
        const newPassword = 'newPassword';
        const currentPassword = 'currentPassword';

        passwordService.save(newPassword, currentPassword).subscribe();

        expect(postStub).toHaveBeenCalledOnce();
        expect(postStub).toHaveBeenCalledWith(postURL, { currentPassword, newPassword });
    });
});
