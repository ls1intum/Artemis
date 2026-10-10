import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { presentationAssessmentFeatureGuard } from 'app/presentation/manage/presentation-assessment-feature.guard';

describe('presentationAssessmentFeatureGuard', () => {
    let courseManagementService: Pick<CourseManagementService, 'find'>;
    let featureToggleService: Pick<FeatureToggleService, 'getFeatureToggleActive'>;

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
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                { provide: CourseManagementService, useValue: courseManagementService },
                { provide: FeatureToggleService, useValue: featureToggleService },
            ],
        });
    });

    async function runGuard(): Promise<boolean | UrlTree> {
        const result = TestBed.runInInjectionContext(() => presentationAssessmentFeatureGuard(route, {} as RouterStateSnapshot));
        expect(isObservable(result)).toBe(true);
        return await firstValueFrom(result as Observable<boolean | UrlTree>);
    }

    function serialize(result: boolean | UrlTree): string {
        expect(result).toBeInstanceOf(UrlTree);
        return TestBed.inject(Router).serializeUrl(result as UrlTree);
    }

    it('should allow navigation when the global feature toggle and the course setting are enabled', async () => {
        await expect(runGuard()).resolves.toBe(true);
        expect(featureToggleService.getFeatureToggleActive).toHaveBeenCalledWith(FeatureToggle.PresentationAssessments);
        expect(courseManagementService.find).toHaveBeenCalledWith(courseId);
    });

    it('should redirect to the course overview when the global feature toggle is disabled', async () => {
        vi.mocked(featureToggleService.getFeatureToggleActive).mockReturnValue(of(false));

        expect(serialize(await runGuard())).toBe('/course-management/1');
    });

    it('should redirect to the course overview when presentation assessments are disabled in the course', async () => {
        vi.mocked(courseManagementService.find).mockReturnValue(of(new HttpResponse({ body: { id: courseId, presentationAssessmentsEnabled: false } as Course })));

        expect(serialize(await runGuard())).toBe('/course-management/1');
    });

    it('should redirect to the course overview when loading the course fails', async () => {
        vi.mocked(courseManagementService.find).mockReturnValue(throwError(() => new Error('course load failed')));

        expect(serialize(await runGuard())).toBe('/course-management/1');
    });
});
