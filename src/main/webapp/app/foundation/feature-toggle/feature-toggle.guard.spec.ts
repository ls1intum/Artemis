import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Observable, firstValueFrom, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { featureToggleGuard } from 'app/foundation/feature-toggle/feature-toggle.guard';

describe('featureToggleGuard', () => {
    let featureToggleService: FeatureToggleService;
    let router: MockRouter;

    // The guard ignores its arguments, so empty snapshots are sufficient.
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [
                { provide: FeatureToggleService, useValue: { getFeatureToggleActive: vi.fn() } },
                { provide: Router, useValue: router },
            ],
        });
        featureToggleService = TestBed.inject(FeatureToggleService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow activation when the feature is active', async () => {
        const activeStub = vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));

        const result = TestBed.runInInjectionContext(() => featureToggleGuard(FeatureToggle.ProgrammingExercises)(route, state)) as Observable<boolean | UrlTree>;

        expect(activeStub).toHaveBeenCalledWith(FeatureToggle.ProgrammingExercises);
        await expect(firstValueFrom(result)).resolves.toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect to the application root when the feature is inactive', async () => {
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(false));
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        const result = TestBed.runInInjectionContext(() => featureToggleGuard(FeatureToggle.ProgrammingExercises)(route, state)) as Observable<boolean | UrlTree>;

        await expect(firstValueFrom(result)).resolves.toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
    });
});
