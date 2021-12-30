import { async, TestBed } from '@angular/core/testing';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { PasswordService } from 'app/account/password/password.service';
import { HttpClient } from '@angular/common/http';

describe('ActivateService', () => {
    let passwordService: PasswordService;
    let httpService: HttpClient;
    let postStub: jest.SpyInstance;

    const postURL = SERVER_API_URL + 'api/account/change-password';

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                passwordService = TestBed.inject(PasswordService);
                httpService = TestBed.inject(HttpClient);
                postStub = jest.spyOn(httpService, 'post');
            });
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set a new password for the current user', async () => {
        const newPassword = 'newPassword';
        const currentPassword = 'currentPassword';

        await passwordService.save(newPassword, currentPassword);

        expect(postStub).toHaveBeenCalledTimes(1);
        expect(postStub).toHaveBeenCalledWith(postURL, { currentPassword, newPassword });
    });
});
