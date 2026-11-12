import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { firstValueFrom } from 'rxjs';
import { adminScienceGuard } from 'app/admin/science/admin-science.guard';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

describe('adminScienceGuard', () => {
    function runGuard(atlasActive: boolean, scienceActive: boolean) {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [MockProvider(ProfileService), MockProvider(FeatureToggleService), MockProvider(Router)],
        });
        const profileService = TestBed.inject(ProfileService);
        const featureToggleService = TestBed.inject(FeatureToggleService);
        const router = TestBed.inject(Router);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => atlasActive && feature === MODULE_FEATURE_ATLAS);
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(scienceActive));
        vi.spyOn(router, 'createUrlTree').mockReturnValue({} as UrlTree);
        return { result: TestBed.runInInjectionContext(() => adminScienceGuard({} as never, {} as never)), router };
    }

    it('should allow the page while the atlas module and the science toggle are both active', async () => {
        await expect(firstValueFrom(runGuard(true, true).result as never)).resolves.toBe(true);
    });

    it.each([
        ['the atlas module is inactive', false, true],
        ['the science feature toggle is off', true, false],
        ['neither is active', false, false],
    ])('should redirect to the administration root while %s', async (_case, atlasActive, scienceActive) => {
        const { result, router } = runGuard(atlasActive, scienceActive);

        await expect(firstValueFrom(result as never)).resolves.not.toBe(true);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/admin']);
    });
});
