import { TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import sinonChai from 'sinon-chai';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { MockFeatureToggleService } from '../helpers/mocks/service/mock-feature-toggle.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('AccountService', () => {
    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: SinonStub;
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
        getStub = stub(httpService, 'get');

        expect(accountService.userIdentity).to.be.undefined;
        expect(accountService.isAuthenticated()).to.be.false;
    });

    afterEach(() => {
        getStub.restore();
    });

    it('should fetch the user on identity if the userIdentity is not defined yet', async () => {
        getStub.returns(of({ body: user }));

        const userReceived = await accountService.identity(false);

        expect(getStub).to.have.been.calledOnceWithExactly(getUserUrl, { observe: 'response' });
        expect(userReceived).to.deep.equal(user);
        expect(accountService.userIdentity).to.deep.equal(user);
        expect(accountService.isAuthenticated()).to.be.true;
    });

    it('should fetch the user on identity if the userIdentity is defined yet (force=true)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).to.deep.equal(user);
        expect(accountService.isAuthenticated()).to.be.true;
        getStub.returns(of({ body: user2 }));

        const userReceived = await accountService.identity(true);

        expect(getStub).to.have.been.calledOnceWithExactly(getUserUrl, { observe: 'response' });
        expect(userReceived).to.deep.equal(user2);
        expect(accountService.userIdentity).to.deep.equal(user2);
        expect(accountService.isAuthenticated()).to.be.true;
    });

    it('should NOT fetch the user on identity if the userIdentity is defined (force=false)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).to.deep.equal(user);
        expect(accountService.isAuthenticated()).to.be.true;
        getStub.returns(of({ body: user2 }));

        const userReceived = await accountService.identity(false);

        expect(getStub).not.to.have.been.called;
        expect(userReceived).to.deep.equal(user);
        expect(accountService.userIdentity).to.deep.equal(user);
        expect(accountService.isAuthenticated()).to.be.true;
    });
});
