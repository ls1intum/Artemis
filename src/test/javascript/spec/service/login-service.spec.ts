import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { of, throwError } from 'rxjs';
import { MockWebsocketService } from '../mocks/mock-websocket.service';
import { MockRouter } from '../mocks/mock-router.service';
import { MockAccountService } from '../mocks/mock-account.service';
import { MockAuthServerProviderService } from '../mocks/mock-auth-server-provider.service';
import { MockAlertService } from '../mocks/mock-alert.service';
import { IAccountService } from 'app/core/auth/account.service';
import { IWebsocketService } from 'app/core/websocket/websocket.service';
import { LoginService } from 'app/core/login/login.service';
import { IAuthServerProvider } from 'app/core/auth/auth-jwt.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('LoginService', () => {
    let accountService: IAccountService;
    let websocketService: IWebsocketService;
    let authServerProvider: IAuthServerProvider;
    let router: MockRouter;
    let alertService: MockAlertService;
    let loginService: LoginService;

    let removeAuthTokenFromCachesStub: SinonStub;
    let authenticateStub: SinonStub;
    let alertServiceClearStub: SinonStub;
    let navigateByUrlStub: SinonStub;
    let alertServiceErrorStub: SinonStub;

    beforeEach(() => {
        accountService = new MockAccountService();
        websocketService = new MockWebsocketService();
        authServerProvider = new MockAuthServerProviderService();
        router = new MockRouter();
        alertService = new MockAlertService();
        // @ts-ignore
        loginService = new LoginService(accountService, websocketService, authServerProvider, router, alertService);

        removeAuthTokenFromCachesStub = stub(authServerProvider, 'removeAuthTokenFromCaches');
        authenticateStub = stub(accountService, 'authenticate');
        alertServiceClearStub = stub(alertService, 'clear');
        navigateByUrlStub = stub(router, 'navigateByUrl');
        alertServiceErrorStub = stub(alertService, 'error');
    });

    afterEach(() => {
        removeAuthTokenFromCachesStub.restore();
        authenticateStub.restore();
        alertServiceClearStub.restore();
        navigateByUrlStub.restore();
        alertServiceErrorStub.restore();
    });

    it('should properly log out when every action is successful', () => {
        removeAuthTokenFromCachesStub.returns(of(null));
        navigateByUrlStub.returns(Promise.resolve(true));
        loginService.logout();

        expect(removeAuthTokenFromCachesStub).to.have.been.calledOnceWithExactly();
        expect(authenticateStub).to.have.been.calledOnceWithExactly(null);
        expect(alertServiceClearStub).to.have.been.calledOnceWithExactly();
        expect(navigateByUrlStub).to.have.been.calledOnceWithExactly('/');
        expect(alertServiceErrorStub).not.to.have.been.called;
    });

    it('should emit an error when an action fails', () => {
        const error = 'fatal error';
        removeAuthTokenFromCachesStub.returns(of(null));
        authenticateStub.throws(throwError(error));
        loginService.logout();

        expect(removeAuthTokenFromCachesStub).to.have.been.calledOnceWithExactly();
        expect(authenticateStub).to.have.been.calledOnceWithExactly(null);
        expect(alertServiceClearStub).not.to.have.been.called;
        expect(navigateByUrlStub).not.to.have.been.called;
        expect(alertServiceErrorStub).to.have.been.calledOnce;
    });
});
