import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_HYPERION, MODULE_FEATURE_IRIS, MODULE_FEATURE_METIS } from 'app/app.constants';
import { DataGuard } from 'app/account/user/settings/data-guard.service';

describe('DataGuard', () => {
    let guard: DataGuard;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: ProfileService, useClass: MockProfileService }],
        });
        guard = TestBed.inject(DataGuard);
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([MODULE_FEATURE_IRIS, MODULE_FEATURE_ATHENA, MODULE_FEATURE_HYPERION, MODULE_FEATURE_METIS])(
        'should allow the AI settings when %s is the only module that sends data to a language model',
        (activeFeature) => {
            const isActive = vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => feature === activeFeature);

            expect(guard.canActivate()).toBe(true);
            expect(guard.isUsingLLM()).toBe(true);
            expect(isActive).toHaveBeenCalledWith(activeFeature);
        },
    );

    it('should refuse the AI settings when no module sends data to a language model', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        expect(guard.canActivate()).toBe(false);
        expect(guard.isUsingLLM()).toBe(false);
    });
});
