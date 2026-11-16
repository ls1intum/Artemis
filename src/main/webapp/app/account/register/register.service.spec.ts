/**
 * Vitest tests for RegisterService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { RegisterService } from 'app/account/register/register.service';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { NEVER, of, throwError } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { EMAIL_ALLOWED_TIMEOUT_MS } from 'app/account/shared/email-allowed';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { User } from 'app/account/user/user.model';

describe('RegisterService', () => {
    let service: RegisterService;
    let httpClient: HttpClient;
    let postSpy: ReturnType<typeof vi.spyOn>;

    const postURL = 'api/core/public/register';

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        });
        await TestBed.compileComponents();

        service = TestBed.inject(RegisterService);
        httpClient = TestBed.inject(HttpClient);
        postSpy = vi.spyOn(httpClient, 'post');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should send a POST request to register a new user', () => {
        const userData = new User();
        userData.login = 'testuser';
        userData.email = 'test@example.com';
        userData.firstName = 'Test';
        userData.lastName = 'User';
        userData.password = 'password123';
        userData.langKey = 'en';

        service.registerUser(userData).subscribe();

        expect(postSpy).toHaveBeenCalledOnce();
        expect(postSpy).toHaveBeenCalledWith(postURL, userData);
    });

    describe('isEmailAllowed', () => {
        const checkURL = 'api/core/public/email-allowed';

        it.each([true, false])('should ask the server and return its answer: allowed=%s', (allowed) => {
            postSpy.mockReturnValue(of({ allowed }));
            let result: boolean | undefined;

            service.isEmailAllowed('ada@example.com').subscribe((value) => (result = value));

            expect(postSpy).toHaveBeenCalledExactlyOnceWith(checkURL, { email: 'ada@example.com' }, { context: expect.any(HttpContext) });
            expect(result).toBe(allowed);
        });

        it('should not show the alert for failed requests, because a failure of this background check is not an error for the person', () => {
            postSpy.mockReturnValue(of({ allowed: true }));

            service.isEmailAllowed('ada@example.com').subscribe();

            const options = postSpy.mock.calls[0][2] as { context: HttpContext };
            expect(options.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        });

        it('should let the person continue when the server does not answer in time', async () => {
            vi.useFakeTimers();
            try {
                postSpy.mockReturnValue(NEVER);
                let result: boolean | undefined;

                service.isEmailAllowed('ada@example.com').subscribe((value) => (result = value));
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_TIMEOUT_MS - 1);
                expect(result).toBeUndefined();
                await vi.advanceTimersByTimeAsync(2);

                expect(result).toBe(true);
            } finally {
                vi.useRealTimers();
            }
        });

        it('should not block the form when the server cannot answer, because the server decides again when the form is sent', () => {
            postSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
            let result: boolean | undefined;

            service.isEmailAllowed('ada@example.com').subscribe((value) => (result = value));

            expect(result).toBe(true);
        });
    });
});
