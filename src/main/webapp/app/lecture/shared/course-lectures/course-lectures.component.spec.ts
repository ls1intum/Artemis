import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { HttpResponse } from '@angular/common/http';
import { LtiService } from 'app/foundation/service/lti.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

describe('CourseLecturesComponent', () => {
    let component: CourseLecturesComponent;
    let fixture: ComponentFixture<CourseLecturesComponent>;
    let ltiService: LtiService;
    let courseOverviewService: CourseOverviewService;
    let lectureService: LectureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseLecturesComponent],
            declarations: [],
            providers: [
                LtiService,
                MockProvider(CourseStorageService, {
                    getCourse: () => ({ id: 1, lectures: [] }) as Course,
                    subscribeToCourseUpdates: () => of({} as Course),
                }),
                MockProvider(CourseOverviewService),
                MockProvider(LectureService, {
                    find: () => of(new HttpResponse({ body: new Lecture() })),
                    // The lectures tab loads its own lectures instead of reading them off the course
                    findAllByCourseIdForOverview: () => of([] as LectureForOverview[]),
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                        firstChild: {
                            snapshot: {
                                params: {},
                            },
                        },
                        queryParams: of({}),
                    },
                },
            ],
        });

        fixture = TestBed.createComponent(CourseLecturesComponent);
        component = fixture.componentInstance;
        ltiService = TestBed.inject(LtiService);
        courseOverviewService = TestBed.inject(CourseOverviewService);
        lectureService = TestBed.inject(LectureService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the lectures of the course itself rather than reading them off the course', async () => {
        const lecture: LectureForOverview = { id: 7, title: 'Lecture 7' };
        const loadSpy = vi.spyOn(lectureService, 'findAllByCourseIdForOverview').mockReturnValue(of([lecture]));
        vi.spyOn(courseOverviewService, 'sortLectures').mockReturnValue([lecture]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);

        component.ngOnInit();

        expect(loadSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(component.lectures()).toEqual([lecture]);
    });

    it('should reuse the loaded lectures when the routed tab component is recreated during the same course visit', () => {
        const lectures: LectureForOverview[] = [{ id: 7, title: 'Lecture 7' }];
        const loadSpy = vi.spyOn(lectureService, 'findAllByCourseIdForOverview').mockReturnValue(of(lectures));
        vi.spyOn(courseOverviewService, 'sortLectures').mockReturnValue(lectures);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);

        component.ngOnInit();
        component.ngOnDestroy();
        const returnedFixture = TestBed.createComponent(CourseLecturesComponent);
        const returnedComponent = returnedFixture.componentInstance;
        returnedComponent.ngOnInit();

        expect(loadSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(returnedComponent.lectures()).toBe(lectures);
        expect(returnedComponent.sortedLectures).toBe(lectures);

        returnedComponent.ngOnDestroy();
        returnedFixture.destroy();
    });

    it('should expose an empty sidebar when the lecture overview request fails', () => {
        vi.spyOn(lectureService, 'findAllByCourseIdForOverview').mockReturnValue(throwError(() => new Error('network')));
        const processSpy = vi.spyOn(component, 'processLectures');

        component.ngOnInit();

        expect(component.lectures()).toEqual([]);
        expect(processSpy).toHaveBeenCalledExactlyOnceWith([]);
        expect(component.sidebarData()).toEqual({
            groupByCategory: true,
            storageId: 'lecture',
            groupedData: component.accordionLectureGroups,
            ungroupedData: component.sidebarLectures,
        });
    });

    it('should navigate to the last selected lecture before considering an upcoming lecture', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        vi.spyOn(TestBed.inject(SessionStorageService), 'retrieve').mockReturnValue('41');
        const upcomingSpy = vi.spyOn(courseOverviewService, 'getUpcomingLecture').mockReturnValue({ id: 42 } as Lecture);

        component.navigateToLecture();

        expect(upcomingSpy).toHaveBeenCalledWith(undefined);
        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['41'], { relativeTo: TestBed.inject(ActivatedRoute), replaceUrl: true });
    });

    it('should navigate to the upcoming lecture when none was selected previously', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        vi.spyOn(TestBed.inject(SessionStorageService), 'retrieve').mockReturnValue(undefined);
        vi.spyOn(courseOverviewService, 'getUpcomingLecture').mockReturnValue({ id: 42 } as Lecture);

        component.navigateToLecture();

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith([42], { relativeTo: TestBed.inject(ActivatedRoute), replaceUrl: true });
    });

    it('should leave sidebar data unchanged until lectures have loaded', () => {
        const processSpy = vi.spyOn(component, 'processLectures');

        component.prepareSidebarData();

        expect(processSpy).not.toHaveBeenCalled();
        expect(component.sidebarData()).toBeUndefined();
    });

    it('should update the page title and persist both sidebar collapse states', () => {
        const persistSpy = vi.spyOn(courseOverviewService, 'setSidebarCollapseState');

        component.setPageTitle('Lectures');
        component.toggleSidebar();
        component.toggleSidebar();

        expect(component.pageTitle()).toBe('Lectures');
        expect(component.isCollapsed()).toBe(false);
        expect(persistSpy).toHaveBeenNthCalledWith(1, 'lecture', true);
        expect(persistSpy).toHaveBeenNthCalledWith(2, 'lecture', false);
    });

    it('should navigate after a lecture subroute closes but not while another child remains active', () => {
        const route = TestBed.inject(ActivatedRoute);
        const navigateSpy = vi.spyOn(component, 'navigateToLecture');

        component.onSubRouteDeactivate();
        expect(navigateSpy).not.toHaveBeenCalled();

        (route as any).firstChild = undefined;
        component.onSubRouteDeactivate();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should handle multi-launch subscription', async () => {
        const processSpy = vi.spyOn(component, 'processLectures');
        const sortSpy = vi.spyOn(courseOverviewService, 'sortLectures').mockReturnValue([]);
        const mapSpy = vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);

        ltiService.setMultiLaunch(true);
        component.ngOnInit();

        expect(component.isMultiLaunch).toBe(true);

        ltiService.setMultiLaunch(false);

        expect(component.isMultiLaunch).toBe(false);
        expect(processSpy).toHaveBeenCalledTimes(1);
        expect(sortSpy).toHaveBeenCalledTimes(1);
        expect(mapSpy).toHaveBeenCalledTimes(1);
    });

    it('should fetch lectures for multi-launch when lectureIDs are provided', async () => {
        const lecture1 = new Lecture();
        lecture1.id = 1;
        const lecture2 = new Lecture();
        lecture2.id = 2;

        vi.spyOn(lectureService, 'find').mockImplementation((id: number) => of(new HttpResponse({ body: id === 1 ? lecture1 : lecture2 })));

        (TestBed.inject(ActivatedRoute) as any).queryParams = of({ lectureIDs: '1,2' });

        component.ngOnInit();

        expect(component.multiLaunchLectureIDs).toEqual([1, 2]);
        expect(lectureService.find).toHaveBeenCalledTimes(2);
        expect(lectureService.find).toHaveBeenCalledWith(1);
        expect(lectureService.find).toHaveBeenCalledWith(2);
    });
});
