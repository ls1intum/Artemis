import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { MockFeatureToggleService } from '../helpers/mocks/service/mock-feature-toggle.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

describe('AccountService', () => {
    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: jest.SpyInstance;
    let translateService: TranslateService;

    const getUserUrl = 'api/account';
    const user = { id: 1, groups: ['USER'] } as User;
    const user2 = { id: 2, groups: ['USER'] } as User;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        httpService = new MockHttpService();
        translateService = TestBed.inject(TranslateService);
        // @ts-ignore
        accountService = new AccountService(translateService, new MockSyncStorage(), httpService, new MockWebsocketService(), new MockFeatureToggleService());
        getStub = jest.spyOn(httpService, 'get');

        expect(accountService.userIdentity).toBe(undefined);
        expect(accountService.isAuthenticated()).toBe(false);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch the user on identity if the userIdentity is not defined yet', async () => {
        getStub.mockReturnValue(of({ body: user }));

        const userReceived = await accountService.identity(false);

        expect(getStub).toHaveBeenCalledTimes(1);
        expect(getStub).toHaveBeenCalledWith(getUserUrl, { observe: 'response' });
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should fetch the user on identity if the userIdentity is defined yet (force=true)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(true);

        expect(getStub).toHaveBeenCalledTimes(1);
        expect(getStub).toHaveBeenCalledWith(getUserUrl, { observe: 'response' });
        expect(userReceived).toEqual(user2);
        expect(accountService.userIdentity).toEqual(user2);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should NOT fetch the user on identity if the userIdentity is defined (force=false)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(false);

        expect(getStub).not.toHaveBeenCalled();
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });
});
