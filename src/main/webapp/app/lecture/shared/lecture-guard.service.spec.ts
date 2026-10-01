import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { LectureGuard } from 'app/lecture/shared/lecture-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_LECTURE } from 'app/app.constants';

describe('LectureGuard', () => {
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
    });

    it('should deny activation and redirect to home when lecture module is disabled', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const result = guard.canActivate();

        expect(result).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(result as UrlTree)).toBe('/');
        expect(profileService.isModuleFeatureActive).toHaveBeenCalledWith(MODULE_FEATURE_LECTURE);
    });
});
