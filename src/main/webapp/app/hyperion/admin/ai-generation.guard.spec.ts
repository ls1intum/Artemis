import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { MODULE_FEATURE_AIWORKER, MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AiGenerationGuard } from './ai-generation.guard';

describe('AiGenerationGuard', () => {
    const isModuleFeatureActive = vi.fn();
    beforeEach(() => {
        isModuleFeatureActive.mockReset();
        TestBed.configureTestingModule({
            providers: [AiGenerationGuard, { provide: ProfileService, useValue: { isModuleFeatureActive } }, provideRouter([])],
        });
    });

    it('allows an enabled generation module', () => {
        isModuleFeatureActive.mockReturnValue(true);
        expect(TestBed.inject(AiGenerationGuard).canActivate()).toBe(true);
        expect(isModuleFeatureActive).toHaveBeenCalledWith(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION);
    });

    it('allows independent worker monitoring when Hyperion is off', () => {
        isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_AIWORKER);
        expect(TestBed.inject(AiGenerationGuard).canActivate()).toBe(true);
    });

    it('rejects access when both modules are inactive', () => {
        isModuleFeatureActive.mockReturnValue(false);
        const result = TestBed.inject(AiGenerationGuard).canActivate();
        expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/');
    });
});
