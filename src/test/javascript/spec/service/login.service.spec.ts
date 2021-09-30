import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { of, throwError } from 'rxjs';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { MockAuthServerProviderService } from '../helpers/mocks/service/mock-auth-server-provider.service';
import { MockAlertService } from '../helpers/mocks/service/mock-alert.service';
import { IAccountService } from 'app/core/auth/account.service';
import { IWebsocketService } from 'app/core/websocket/websocket.service';
import { LoginService } from 'app/core/login/login.service';
import { IAuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { MockNotificationService } from '../helpers/mocks/service/mock-notification.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('LoginService', () => {
    let accountService: IAccountService;
    let websocketService: IWebsocketService;
    let authServerProvider: IAuthServerProvider;
    let router: MockRouter;
    let alertService: MockAlertService;
    let notificationService: MockNotificationService;
    let loginService: LoginService;

    let removeAuthTokenFromCachesStub: SinonStub;
    let authenticateStub: SinonStub;
    // let alertServiceClearStub: SinonStub;
    let notificationServiceCleanUpStub: SinonStub;
    let navigateByUrlStub: SinonStub;
    let alertServiceErrorStub: SinonStub;

    // TODO: reimplement this test using the typical TestBed configuration as in other tests

    beforeEach(() => {
        accountService = new MockAccountService();
        websocketService = new MockWebsocketService();
        authServerProvider = new MockAuthServerProviderService();
        router = new MockRouter();
        alertService = new MockAlertService();
        notificationService = new MockNotificationService();
        // @ts-ignore
        loginService = new LoginService(accountService, websocketService, authServerProvider, router, alertService, notificationService);

        removeAuthTokenFromCachesStub = stub(authServerProvider, 'removeAuthTokenFromCaches');
        authenticateStub = stub(accountService, 'authenticate');
        // alertServiceClearStub = stub(alertService, 'clear');
        notificationServiceCleanUpStub = stub(notificationService, 'cleanUp');
        navigateByUrlStub = stub(router, 'navigateByUrl');
        alertServiceErrorStub = stub(alertService, 'error');
    });

    afterEach(() => {
        removeAuthTokenFromCachesStub.restore();
        authenticateStub.restore();
        // alertServiceClearStub.restore();
        notificationServiceCleanUpStub.restore();
        navigateByUrlStub.restore();
        alertServiceErrorStub.restore();
    });

    it('should properly log out when every action is successful', () => {
        removeAuthTokenFromCachesStub.returns(of(undefined));
        navigateByUrlStub.returns(Promise.resolve(true));
        loginService.logout(true);

        expect(removeAuthTokenFromCachesStub).to.have.been.calledOnceWithExactly();
        expect(authenticateStub).to.have.been.calledOnceWithExactly(undefined);
        // expect(alertServiceClearStub).to.have.been.calledOnceWithExactly();
        expect(notificationServiceCleanUpStub).to.have.been.calledOnceWithExactly();
        expect(navigateByUrlStub).to.have.been.calledOnceWithExactly('/');
        expect(alertServiceErrorStub).not.to.have.been.called;
    });

    it('should emit an error when an action fails', () => {
        const error = 'fatal error';
        removeAuthTokenFromCachesStub.returns(of(undefined));
        authenticateStub.throws(throwError(error));
        loginService.logout(true);

        expect(removeAuthTokenFromCachesStub).to.have.been.calledOnceWithExactly();
        expect(authenticateStub).to.have.been.calledOnceWithExactly(undefined);
        // expect(alertServiceClearStub).not.to.have.been.called;
        expect(navigateByUrlStub).not.to.have.been.called;
        expect(alertServiceErrorStub).to.have.been.calledOnce;
    });
});
