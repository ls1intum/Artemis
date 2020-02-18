import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { AllowedOrionVersionRange, OrionVersionValidator } from 'app/orion/outdated-plugin-warning/orion-version-validator.service';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { MockProfileService } from '../../mocks/mock-profile.service';
import { MockWindowRef } from '../../mocks/mock-window.service';
import { MockRouter } from '../../mocks/mock-router.service';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/layouts/profiles/profile-info.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionValidatorService', () => {
    let orionVersionValidator: OrionVersionValidator;
    let profileService: ProfileService;
    let windowRef: MockWindowRef;
    let router: Router;

    let profileInfoStub: SinonStub;
    let navigateSpy: SinonSpy;

    const allowedVersion = { from: '1.0.0', to: '1.1.0' } as AllowedOrionVersionRange;
    const profileInfo = { allowedOrionVersions: allowedVersion } as ProfileInfo;
    const userAgent = 'Some user agent ';
    const versionTooLow = 'Orion/0.9.0';
    const versionTooHigh = 'Orion/1.1.1';
    const versionCorrect = 'Orion/1.0.5';
    const legacy = 'IntelliJ';

    beforeEach(() => {
        profileService = new MockProfileService();
        windowRef = new MockWindowRef();
        router = new MockRouter();
        orionVersionValidator = new OrionVersionValidator(profileService, windowRef, router);

        profileInfoStub = stub(profileService, 'getProfileInfo');
        navigateSpy = spy(router, 'navigateByUrl');

        profileInfoStub.returns(of(profileInfo));
    });

    afterEach(() => {
        profileInfoStub.restore();
        navigateSpy.restore();
    });

    it('should route to the error page if a legacy version is used', () => {
        windowRef.mockUserAgent = userAgent + legacy;
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.not.have.been.called;
        expect(navigateSpy).to.have.been.calledOnceWithExactly('/orionOutdated?versionString=soOldThatThereIsNoVersion');
    });

    it('should route to the error page if the version is too high', () => {
        windowRef.mockUserAgent = userAgent + versionTooHigh;
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.have.been.calledOnce;
        expect(navigateSpy).to.have.been.called.calledOnceWithExactly(`/orionOutdated?versionString=1.1.1`);
    });

    it('should route to the error page if the version is too low', () => {
        windowRef.mockUserAgent = userAgent + versionTooLow;
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.have.been.calledOnce;
        expect(navigateSpy).to.have.been.calledOnceWithExactly(`/orionOutdated?versionString=0.9.0`);
    });

    it('should accept the correct version', () => {
        windowRef.mockUserAgent = userAgent + versionCorrect;
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.have.been.calledOnce;
        expect(navigateSpy).to.not.have.been.called;
    });

    it('should not do anything if a normal browser is connected', () => {
        windowRef.mockUserAgent = userAgent;

        orionVersionValidator.validateOrionVersion();

        expect(profileInfoStub).to.not.have.been.called;
        expect(navigateSpy).to.not.have.been.called;
    });
});
