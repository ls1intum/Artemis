/**
 * Vitest tests for RegisterService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { RegisterService } from 'app/core/account/register/register.service';
import { HttpClient } from '@angular/common/http';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { User } from 'app/core/user/user.model';

describe('RegisterService', () => {
    setupTestBed({ zoneless: true });

    let service: RegisterService;
    let httpClient: HttpClient;
    let postSpy: ReturnType<typeof vi.spyOn>;

    const postURL = 'api/core/public/register';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(RegisterService);
                httpClient = TestBed.inject(HttpClient);
                postSpy = vi.spyOn(httpClient, 'post');
            });
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
});
