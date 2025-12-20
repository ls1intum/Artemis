/**
 * Vitest tests for PasswordResetInitService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { PasswordResetInitService } from 'app/core/account/password-reset/init/password-reset-init.service';
import { HttpClient } from '@angular/common/http';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';

describe('PasswordResetInitService', () => {
    setupTestBed({ zoneless: true });

    let service: PasswordResetInitService;
    let httpClient: HttpClient;
    let postSpy: ReturnType<typeof vi.spyOn>;

    const postURL = 'api/core/public/account/reset-password/init';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(PasswordResetInitService);
                httpClient = TestBed.inject(HttpClient);
                postSpy = vi.spyOn(httpClient, 'post');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send a POST request to init password reset with email', () => {
        const email = 'user@example.com';

        service.save(email).subscribe();

        expect(postSpy).toHaveBeenCalledOnce();
        expect(postSpy).toHaveBeenCalledWith(postURL, email);
    });
});
