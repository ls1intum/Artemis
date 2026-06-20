import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Router } from '@angular/router';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { GocastGuard } from 'app/videosource/gocast/gocast-guard.service';
import { ActiveFeatureToggles, FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';

describe('GocastGuard', () => {
    setupTestBed({ zoneless: true });

    let guard: GocastGuard;
    let router: Router;
    let featureSubject: BehaviorSubject<ActiveFeatureToggles>;

    beforeEach(() => {
        const routerMock = {
            navigate: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [GocastGuard, { provide: FeatureToggleService, useClass: MockFeatureToggleService }, { provide: Router, useValue: routerMock }],
        });

        guard = TestBed.inject(GocastGuard);
        router = TestBed.inject(Router);
        // Access the subject through the mock so we can control feature toggle state directly.
        featureSubject = (TestBed.inject(FeatureToggleService) as unknown as MockFeatureToggleService)['subject'] as BehaviorSubject<ActiveFeatureToggles>;
    });

    it('should allow access when the Gocast feature toggle is active', async () => {
        featureSubject.next(Object.values(FeatureToggle));

        const canActivate = await firstValueFrom(guard.canActivate());

        expect(canActivate).toBe(true);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should deny access and redirect to /course-management when the Gocast feature toggle is inactive', async () => {
        featureSubject.next(Object.values(FeatureToggle).filter((f) => f !== FeatureToggle.Gocast));

        const canActivate = await firstValueFrom(guard.canActivate());

        expect(canActivate).toBe(false);
        expect(router.navigate).toHaveBeenCalledWith(['/course-management']);
    });
});
