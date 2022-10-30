import { OrionVersionValidator } from 'app/shared/orion/outdated-plugin-warning/orion-version-validator.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockProvider } from 'ng-mocks';

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
            // @ts-ignore
            window.navigator = Object.create(navigator, {
                userAgent: userAgentProp,
            });
        }
    }
}

describe('OrionValidatorService', () => {
    let orionVersionValidator: OrionVersionValidator;
    let profileInfoStub: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    const allowedVersion = '1.0.0';
    const profileInfo = { allowedMinimumOrionVersion: allowedVersion } as ProfileInfo;
    const userAgent = 'Some user agent ';
    const versionTooLow = 'Orion/0.9.0';
    const versionCorrect = 'Orion/1.0.5';
    const legacy = 'IntelliJ';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [OrionVersionValidator, MockProvider(Router), MockProvider(ProfileService)],
        });
        orionVersionValidator = TestBed.inject(OrionVersionValidator);

        profileInfoStub = jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        profileInfoStub.mockReturnValue(of(profileInfo));

        navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigateByUrl');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should route to the error page if a legacy version is used', fakeAsync(() => {
        setUserAgent(userAgent + legacy);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion().subscribe((result) => expect(result).toBeFalse());
        tick();

        expect(profileInfoStub).not.toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith('/orion-outdated?versionString=soOldThatThereIsNoVersion');
    }));

    it('should route to the error page if the version is too low', fakeAsync(() => {
        setUserAgent(userAgent + versionTooLow);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion().subscribe((result) => expect(result).toBeFalse());
        tick();

        expect(profileInfoStub).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(`/orion-outdated?versionString=0.9.0`);
    }));

    it('should accept the correct version', fakeAsync(() => {
        setUserAgent(userAgent + versionCorrect);
        orionVersionValidator.isOrion = true;

        orionVersionValidator.validateOrionVersion().subscribe((result) => expect(result).toBeTrue());
        tick();

        expect(profileInfoStub).toHaveBeenCalledOnce();
        expect(navigateSpy).not.toHaveBeenCalled();
    }));

    it('should not do anything if a normal browser is connected', fakeAsync(() => {
        setUserAgent(userAgent);

        orionVersionValidator.validateOrionVersion().subscribe((result) => expect(result).toBeTrue());
        tick();

        expect(profileInfoStub).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    }));

    it('should return stored value', fakeAsync(() => {
        orionVersionValidator.isOrion = true;
        // @ts-ignore
        orionVersionValidator.isValidVersion = true;

        orionVersionValidator.validateOrionVersion().subscribe((result) => expect(result).toBeTrue());
        tick();

        expect(profileInfoStub).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
    }));
});
