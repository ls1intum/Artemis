import { async } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonStub, stub } from 'sinon';
import { Observable, of } from 'rxjs';
import * as sinonChai from 'sinon-chai';
import { MockWebsocketService } from '../mocks/mock-websocket.service';
import { MockLanguageService } from '../helpers/mock-language.service';
import { MockSyncStorage } from '../mocks';
import { MockHttpService } from '../mocks/mock-http.service';
import { MockFeatureToggleService } from '../mocks/mock-feature-toggle-service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('AccountService', () => {
    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: SinonStub;
    let getAuthenticated: Observable<User | null>;

    const getUserUrl = 'undefinedapi/account';
    const user = { id: 1, groups: ['USER'] } as User;
    const user2 = { id: 2, groups: ['USER'] } as User;

    beforeEach(async(() => {
        httpService = new MockHttpService();
        // @ts-ignore
        accountService = new AccountService(new MockLanguageService(), new MockSyncStorage(), httpService, new MockWebsocketService(), new MockFeatureToggleService());
        getStub = stub(httpService, 'get');
        getAuthenticated = accountService.getAuthenticationState();

        expect(accountService.userIdentity).to.deep.equal(null);
        expect(accountService.isAuthenticated()).to.be.false;
    }));

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
