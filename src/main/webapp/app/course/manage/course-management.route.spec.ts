import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Route, RouteReuseStrategy, Routes, UrlSegment, UrlSegmentGroup, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { courseManagementRoutes, presentationAssessmentUrlMatcher } from 'app/course/manage/course-management.route';
import { ArtemisRouteReuseStrategy } from 'app/core/config/artemis-route-reuse.strategy';
import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { SidebarCardMediumComponent } from 'app/course/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/course/sidebar/sidebar-card-item/sidebar-card-item.component';
import { IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

function presentationRoutes(routes: Routes): Routes {
    return routes.flatMap((route) => (route.matcher === presentationAssessmentUrlMatcher ? [route] : presentationRoutes(route.children ?? [])));
}

async function setUpPresentationNavigation() {
    const findAllByCourseId = vi.fn((courseId: number) =>
        of(new HttpResponse({ body: [42, 43].map((id) => ({ id, title: `Course ${courseId}`, courseId, exerciseId: 7, instances: [] })) })),
    );
    const create = vi.fn().mockReturnValue(of(new HttpResponse({ body: { id: 43 } })));
    const getTitlesForCourse = vi.fn(() => of([]));
    const routes = presentationRoutes(courseManagementRoutes);
    expect(routes).toHaveLength(1);
    // Keep the real route data and component; isolate authorization and the surrounding course shell.
    await TestBed.configureTestingModule({
        providers: [
            provideRouter([{ path: 'course-management/:courseId', children: routes.map((route) => ({ ...route, canActivate: [], resolve: {} })) }]),
            { provide: RouteReuseStrategy, useClass: ArtemisRouteReuseStrategy },
            { provide: PresentationAssessmentService, useValue: { findAllByCourseId, create } },
            { provide: ExerciseService, useValue: { getTitlesForCourse } },
            { provide: AlertService, useValue: { success: vi.fn(), addAlert: vi.fn() } },
            { provide: TranslateService, useValue: { instant: (key: string) => key, onLangChange: new Subject(), onTranslationChange: new Subject() } },
        ],
    })
        .overrideComponent(PresentationAssessmentManagementComponent, {
            set: {
                imports: [SidebarCardMediumComponent],
                template: `
                    @for (item of sidebarData().pinnedData; track item.id) {
                        <jhi-medium-sidebar-card [sidebarItem]="item" />
                    }
                    @for (item of sidebarData().groupedData?.['linkedToExercise']?.entityData; track item.id) {
                        <jhi-medium-sidebar-card [sidebarItem]="item" [itemSelected]="true" />
                    }
                `,
            },
        })
        .overrideComponent(SidebarCardItemComponent, { set: { template: '' } })
        .compileComponents();
    const harness = await RouterTestingHarness.create();
    return { harness, findAllByCourseId, create, getTitlesForCourse };
}

describe('presentation assessment url matcher', () => {
    const segmentsOf = (path: string) => path.split('/').map((segment) => new UrlSegment(segment, {}));

    it.each([
        ['presentations', {}],
        ['presentations/42', { presentationId: '42' }],
        ['presentations/42/exercises/7', { presentationId: '42', exerciseId: '7' }],
    ])('matches %s and extracts its parameters', (path, expectedParams) => {
        const segments = segmentsOf(path);

        const result = presentationAssessmentUrlMatcher(segments, {} as UrlSegmentGroup, {} as Route);

        expect(result?.consumed).toEqual(segments);
        expect(Object.fromEntries(Object.entries(result?.posParams ?? {}).map(([name, segment]) => [name, segment.path]))).toEqual(expectedParams);
    });

    it.each(['lectures', 'presentations/42/exercises', 'presentations/42/lectures/7', 'presentations/42/exercises/7/teams', 'other/presentations'])('does not match %s', (path) => {
        expect(presentationAssessmentUrlMatcher(segmentsOf(path), {} as UrlSegmentGroup, {} as Route)).toBeNull();
    });
});

describe('presentation assessment route access', () => {
    it('should open the presentation page for tutors', () => {
        const [route] = presentationRoutes(courseManagementRoutes);

        expect(route.data?.['authorities']).toEqual(IS_AT_LEAST_TUTOR);
    });
});

describe('presentation course navigation', () => {
    it.each(['presentations', 'presentations/42', 'presentations/42/exercises/7'])('reloads the course and targets subsequent writes correctly for %s', async (path) => {
        const { harness, findAllByCourseId, create, getTitlesForCourse } = await setUpPresentationNavigation();
        const first = await harness.navigateByUrl(`/course-management/1/${path}`, PresentationAssessmentManagementComponent);
        const second = await harness.navigateByUrl(`/course-management/2/${path}`, PresentationAssessmentManagementComponent);
        expect(second).toBe(first);
        expect(second.courseId()).toBe(2);
        expect(second.presentationAssessments()[0].courseId).toBe(2);
        expect(findAllByCourseId).toHaveBeenCalledWith(1);
        expect(findAllByCourseId).toHaveBeenCalledWith(2);
        expect(getTitlesForCourse).toHaveBeenCalledWith(1);
        expect(getTitlesForCourse).toHaveBeenCalledWith(2);
        const detail = await harness.navigateByUrl('/course-management/2/presentations/42/exercises/7', PresentationAssessmentManagementComponent);
        const loadCount = findAllByCourseId.mock.calls.length;
        const exerciseLoadCount = getTitlesForCourse.mock.calls.length;
        const selected = await harness.navigateByUrl('/course-management/2/presentations/43/exercises/7', PresentationAssessmentManagementComponent);
        expect(selected).toBe(detail);
        expect(selected.selectedPresentationId()).toBe(43);
        expect(selected.contentReady()).toBe(true);
        const selectedLink = harness.routeNativeElement?.querySelector('a[href="/course-management/2/presentations/43/exercises/7"]');
        expect(selectedLink?.parentElement?.classList.contains('bg-selected')).toBe(true);
        const overviewLink = harness.routeNativeElement?.querySelector('a[href="/course-management/2/presentations"]');
        expect(overviewLink?.parentElement?.classList.contains('bg-selected')).toBe(false);
        expect(selected.sidebarData().groupedData?.['linkedToExercise'].entityData.find((item) => item.id === 43)?.routerLink).toBe(
            '/course-management/2/presentations/43/exercises/7',
        );
        expect(findAllByCourseId).toHaveBeenCalledTimes(loadCount);
        expect(getTitlesForCourse).toHaveBeenCalledTimes(exerciseLoadCount);
        second.handlePresentationDialogSave({ presentationAssessment: { title: 'New presentation', maxPoints: 10 } });
        expect(create).toHaveBeenCalledWith(2, expect.objectContaining({ title: 'New presentation' }));
    });

    it('keeps the component and its loaded data when moving between the overview, a presentation and an exercise-linked presentation', async () => {
        const { harness, findAllByCourseId, getTitlesForCourse } = await setUpPresentationNavigation();
        const overview = await harness.navigateByUrl('/course-management/1/presentations', PresentationAssessmentManagementComponent);
        const loadCount = findAllByCourseId.mock.calls.length;
        const exerciseLoadCount = getTitlesForCourse.mock.calls.length;

        const presentation = await harness.navigateByUrl('/course-management/1/presentations/42', PresentationAssessmentManagementComponent);
        const linkedPresentation = await harness.navigateByUrl('/course-management/1/presentations/43/exercises/7', PresentationAssessmentManagementComponent);
        const backToOverview = await harness.navigateByUrl('/course-management/1/presentations', PresentationAssessmentManagementComponent);

        expect(presentation).toBe(overview);
        expect(linkedPresentation).toBe(overview);
        expect(backToOverview).toBe(overview);
        expect(findAllByCourseId).toHaveBeenCalledTimes(loadCount);
        expect(getTitlesForCourse).toHaveBeenCalledTimes(exerciseLoadCount);
    });
});

describe('courseManagementRoutes', () => {
    const containerRoute = courseManagementRoutes.find((route) => route.path === '' && !!route.children?.length);

    it('does not handle the removed management overview inside the lazy route tree', () => {
        const overviewRoute = courseManagementRoutes.find((route) => route.path === '' && !route.children);

        expect(overviewRoute).toBeUndefined();
        expect(containerRoute).toBeDefined();
    });

    // Regression guard for #13189: the CourseManagementContainerComponent renders a full-bleed layout (its own
    // sidebar, title bar, and module-bg content box), so it must NOT be wrapped in the app-level module-background
    // card (see app.component.html). It must therefore declare usesModuleBackground: false explicitly to override the
    // parent `course-management` route's usesModuleBackground: true. Angular 22 inherits parent route data down to the
    // deepest activated child, so without this explicit false the container was wrapped
    // and shifted right, clipping content at the right edge.
    it('renders the container route full-bleed (usesModuleBackground: false)', () => {
        expect(containerRoute!.data?.['usesModuleBackground']).toBe(false);
    });

    // The course creation page brings the same frame itself (title bar above a scrolling card), so the app-level card
    // would nest a second background around it.
    it('renders the course creation route full-bleed (usesModuleBackground: false)', () => {
        const creationRoute = courseManagementRoutes.find((route) => route.path === 'new');

        expect(creationRoute!.data?.['usesModuleBackground']).toBe(false);
    });

    it('provides course grading inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/grading')).toBe(true);
    });

    it('provides team pages inside the management container', () => {
        expect(containerRoute!.children?.some((route) => route.path === ':courseId/exercises/:exerciseId/teams')).toBe(true);
    });
});
