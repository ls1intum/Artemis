import { TestBed } from '@angular/core/testing';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { PasswordService } from 'app/account/password/password.service';
import { HttpClient } from '@angular/common/http';

describe('PasswordService', () => {
    let passwordService: PasswordService;
    let httpService: HttpClient;
    let postStub: jest.SpyInstance;

    const postURL = 'api/account/change-password';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                passwordService = TestBed.inject(PasswordService);
                httpService = TestBed.inject(HttpClient);
                postStub = jest.spyOn(httpService, 'post');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set a new password for the current user', () => {
        const newPassword = 'newPassword';
        const currentPassword = 'currentPassword';

        passwordService.save(newPassword, currentPassword).subscribe();

        expect(postStub).toHaveBeenCalledOnce();
        expect(postStub).toHaveBeenCalledWith(postURL, { currentPassword, newPassword });
    });
});
