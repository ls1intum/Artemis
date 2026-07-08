import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { scienceSettingsGuard } from 'app/account/user/settings/science-settings/science-settings.guard';

describe('scienceSettingsGuard', () => {
    setupTestBed({ zoneless: true });

    let profileService: ProfileService;
    let router: MockRouter;

    // The guard ignores its arguments, so empty snapshots are sufficient.
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: Router, useValue: router },
            ],
        });
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow activation when the atlas module is active', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => feature === MODULE_FEATURE_ATLAS);

        const result = TestBed.runInInjectionContext(() => scienceSettingsGuard(route, state));

        expect(result).toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect to the user-settings root when the atlas module is inactive (issue #13173)', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        const result = TestBed.runInInjectionContext(() => scienceSettingsGuard(route, state));

        expect(router.createUrlTree).toHaveBeenCalledWith(['/user-settings']);
        expect(result).toBe(redirect);
    });
});
