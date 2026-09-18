import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { presentationAssessmentFeatureGuard } from 'app/presentation/manage/presentation-assessment-feature.guard';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('presentationAssessmentFeatureGuard', () => {
    let courseManagementService: Pick<CourseManagementService, 'find'>;
    let featureToggleService: Pick<FeatureToggleService, 'getFeatureToggleActive'>;
    let router: MockRouter;

    const courseId = 1;
    const route = {
        parent: {
            paramMap: convertToParamMap({ courseId }),
        },
        paramMap: convertToParamMap({}),
    } as ActivatedRouteSnapshot;

    beforeEach(() => {
        courseManagementService = {
            find: vi.fn().mockReturnValue(of(new HttpResponse({ body: { id: courseId, presentationAssessmentsEnabled: true } as Course }))),
        };
        featureToggleService = {
            getFeatureToggleActive: vi.fn().mockReturnValue(of(true)),
        };
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [
                { provide: CourseManagementService, useValue: courseManagementService },
                { provide: FeatureToggleService, useValue: featureToggleService },
                { provide: Router, useValue: router },
            ],
        });
    });

    async function runGuard(): Promise<boolean | UrlTree> {
        const result = TestBed.runInInjectionContext(() => presentationAssessmentFeatureGuard(route, {} as RouterStateSnapshot));
        expect(isObservable(result)).toBe(true);
        return await firstValueFrom(result as Observable<boolean | UrlTree>);
    }

    it('should allow navigation when the global feature toggle and the course setting are enabled', async () => {
        await expect(runGuard()).resolves.toBe(true);
        expect(featureToggleService.getFeatureToggleActive).toHaveBeenCalledWith(FeatureToggle.PresentationAssessments);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect to lectures when the global feature toggle is disabled', async () => {
        vi.mocked(featureToggleService.getFeatureToggleActive).mockReturnValue(of(false));
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        await expect(runGuard()).resolves.toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/course-management', courseId, 'lectures']);
    });

    it('should redirect to lectures when loading the course fails', async () => {
        vi.mocked(courseManagementService.find).mockReturnValue(throwError(() => new Error('course load failed')));
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        await expect(runGuard()).resolves.toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/course-management', courseId, 'lectures']);
    });
});
