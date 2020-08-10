import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { OrionVersionValidator } from 'app/shared/orion/outdated-plugin-warning/orion-version-validator.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { MockProfileService } from '../helpers/mocks/service/mock-profile.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

chai.use(sinonChai);
const expect = chai.expect;

function setUserAgent(userAgent: string) {
    if (window.navigator.userAgent !== userAgent) {
        const userAgentProp = {
            get() {
                return userAgent;
            },
        };
        try {
            Object.defineProperty(window.navigator, 'userAgent', userAgentProp);
        } catch (e) {
            window.navigator = Object.create(navigator, {
                userAgent: userAgentProp,
            });
        }
    }
}

describe('OrionValidatorService', () => {
    let orionVersionValidator: OrionVersionValidator;
    let profileService: ProfileService;
    let router: Router;

    let profileInfoStub: SinonStub;
    let navigateSpy: SinonSpy;

    const allowedVersion = '1.0.0';
    const profileInfo = { allowedMinimumOrionVersion: allowedVersion } as ProfileInfo;
    const userAgent = 'Some user agent ';
    const versionTooLow = 'Orion/0.9.0';
    const versionCorrect = 'Orion/1.0.5';
    const legacy = 'IntelliJ';

    beforeEach(() => {
        profileService = new MockProfileService();
        router = new MockRouter();
        orionVersionValidator = new OrionVersionValidator(profileService, router);

        profileInfoStub = stub(profileService, 'getProfileInfo');
        navigateSpy = spy(router, 'navigateByUrl');

        profileInfoStub.returns(of(profileInfo));
    });

    afterEach(() => {
        profileInfoStub.restore();
        navigateSpy.restore();
    });

    it('should route to the error page if a legacy version is used', () => {
        setUserAgent(userAgent + legacy);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.not.have.been.called;
        expect(navigateSpy).to.have.been.calledOnceWithExactly('/orion-outdated?versionString=soOldThatThereIsNoVersion');
    });

    it('should route to the error page if the version is too low', () => {
        setUserAgent(userAgent + versionTooLow);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion().subscribe();

        expect(profileInfoStub).to.have.been.calledOnce;
        expect(navigateSpy).to.have.been.calledOnceWithExactly(`/orion-outdated?versionString=0.9.0`);
    });

    it('should accept the correct version', () => {
        setUserAgent(userAgent + versionCorrect);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.have.been.calledOnce;
        expect(navigateSpy).to.not.have.been.called;
    });

    it('should not do anything if a normal browser is connected', () => {
        setUserAgent(userAgent);

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.not.have.been.called;
        expect(navigateSpy).to.not.have.been.called;
    });
});
