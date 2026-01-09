import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LectureGuard } from 'app/lecture/shared/lecture-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_LECTURE } from 'app/app.constants';

describe('LectureGuard', () => {
    setupTestBed({ zoneless: true });

    let guard: LectureGuard;
    let profileService: ProfileService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LectureGuard,
                {
                    provide: ProfileService,
                    useValue: {
                        isModuleFeatureActive: vi.fn(),
                    },
                },
                {
                    provide: Router,
                    useValue: {
                        navigate: vi.fn(),
                    },
                },
            ],
        });

        guard = TestBed.inject(LectureGuard);
        profileService = TestBed.inject(ProfileService);
        router = TestBed.inject(Router);
    });

    it('should allow activation when lecture module is enabled', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        const result = guard.canActivate();

        expect(result).toBe(true);
        expect(profileService.isModuleFeatureActive).toHaveBeenCalledWith(MODULE_FEATURE_LECTURE);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should deny activation and redirect to home when lecture module is disabled', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const result = guard.canActivate();

        expect(result).toBe(false);
        expect(profileService.isModuleFeatureActive).toHaveBeenCalledWith(MODULE_FEATURE_LECTURE);
        expect(router.navigate).toHaveBeenCalledWith(['/']);
    });
});
