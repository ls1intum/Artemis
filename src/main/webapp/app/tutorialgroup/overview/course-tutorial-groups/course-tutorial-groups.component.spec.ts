import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/overview/course-tutorial-groups/course-tutorial-groups.component';
import { MockDirective, MockProvider } from 'ng-mocks';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { convertToParamMap } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import dayjs, { Dayjs } from 'dayjs/esm';
import { CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { CourseTutorialGroupDetailContainerComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail-container/course-tutorial-group-detail-container.component';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';

interface TutorialGroupApiServiceMock {
    getTutorialGroupsForCourse: ReturnType<typeof vi.fn>;
}

describe('CourseTutorialGroupsComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;
    let component: CourseTutorialGroupsComponent;

    let courseOverviewService: CourseOverviewService;
    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let courseStorageService: CourseStorageService;
    let lectureService: LectureService;
    let sessionStorageService: SessionStorageService;
    let router: Router;

    const mockRouter = new MockRouter();
    const mockActivatedRoute = createMockActivatedRoute();

    const now = dayjs();
    const tutorialGroup1 = createTutorialGroup(1, 'TG 1 Mon 13', true, false);
    const tutorialGroup2 = createTutorialGroup(2, 'TG 1 Tue 14', false, false);
    const tutorialLecture1 = createTutorialLecture(1, now.subtract(9, 'day'), now.subtract(2, 'day'));
    const tutorialLecture2 = createTutorialLecture(2, now.subtract(1, 'day'), now.add(6, 'day'));

    beforeEach(async () => {
        tutorialGroupApiServiceMock = {
            getTutorialGroupsForCourse: vi.fn().mockReturnValue(of([])),
        };
        await TestBed.configureTestingModule({
            imports: [CourseTutorialGroupsComponent, MockSidebarComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: Router, useValue: mockRouter },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                MockProvider(CourseStorageService),
                { provide: TutorialGroupApi, useValue: tutorialGroupApiServiceMock },
                MockProvider(LectureService),
                MockProvider(CourseOverviewService),
                MockProvider(SessionStorageService),
            ],
        })
            .overrideComponent(CourseTutorialGroupsComponent, {
                remove: { imports: [SidebarComponent] },
                add: { imports: [MockSidebarComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupsComponent);
        component = fixture.componentInstance;

        courseOverviewService = TestBed.inject(CourseOverviewService);
        courseStorageService = TestBed.inject(CourseStorageService);
        lectureService = TestBed.inject(LectureService);
        vi.spyOn(lectureService, 'findAllTutorialLecturesByCourseId').mockReturnValue(of(new HttpResponse({ body: [] })));
        sessionStorageService = TestBed.inject(SessionStorageService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    describe('sidebar toggle sync', () => {
        beforeEach(() => {
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [], lectures: [] });
            // The cached course is empty, and the sidebar is now rebuilt for an empty result too, so these mappers are
            // reached here. MockProvider returns undefined from them; the real ones return an empty array.
            vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([]);
            vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);
        });

        it('should sync the collapse state and a working toggle into an activated tutorial group detail', () => {
            let receivedCollapsed: boolean | undefined;
            let receivedToggle: (() => void) | undefined;
            const setSidebarToggle = vi.fn((collapsed: boolean, toggle: () => void) => {
                receivedCollapsed = collapsed;
                receivedToggle = toggle;
            });
            const detail = Object.assign(Object.create(CourseTutorialGroupDetailContainerComponent.prototype), { setSidebarToggle });

            component.onSubRouteActivate(detail);
            fixture.detectChanges();

            expect(receivedCollapsed).toBe(component.isCollapsed());
            const collapsedBeforeToggle = component.isCollapsed();
            receivedToggle?.();
            expect(component.isCollapsed()).toBe(!collapsedBeforeToggle);
        });

        it('should sync the collapse state and a working toggle into an activated tutorial lecture detail', () => {
            let receivedCollapsed: boolean | undefined;
            let receivedToggle: (() => void) | undefined;
            const setSidebarToggle = vi.fn((collapsed: boolean, toggle: () => void) => {
                receivedCollapsed = collapsed;
                receivedToggle = toggle;
            });
            const detail = Object.assign(Object.create(CourseLectureDetailsComponent.prototype), { setSidebarToggle });

            component.onSubRouteActivate(detail);
            fixture.detectChanges();

            expect(receivedCollapsed).toBe(component.isCollapsed());
            const collapsedBeforeToggle = component.isCollapsed();
            receivedToggle?.();
            expect(component.isCollapsed()).toBe(!collapsedBeforeToggle);
        });
    });

    it('should use cached groups and lectures if available to compute correct sidebar data', async () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [tutorialGroup1, tutorialGroup2], lectures: [tutorialLecture1, tutorialLecture2] });

        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialLecture(tutorialLecture1),
            getSidebarCardElementForTutorialLecture(tutorialLecture2),
        ]);
        vi.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        vi.spyOn(courseOverviewService, 'mapLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        const tutorialGroupFetchSpy = vi.spyOn(tutorialGroupApiServiceMock, 'getTutorialGroupsForCourse').mockReturnValue(of([]));
        const tutorialLectureFetchSpy = vi.spyOn(lectureService, 'findAllTutorialLecturesByCourseId').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupFetchSpy).not.toHaveBeenCalled();
        expect(tutorialLectureFetchSpy).not.toHaveBeenCalled();
        const expectedSidebarCardElement1: SidebarCardElement = getSidebarCardElementForTutorialGroup(tutorialGroup1);
        const expectedSidebarCardElement2: SidebarCardElement = getSidebarCardElementForTutorialGroup(tutorialGroup2);
        const expectedSidebarCardElement3: SidebarCardElement = getSidebarCardElementForTutorialLecture(tutorialLecture1);
        const expectedSidebarCardElement4: SidebarCardElement = getSidebarCardElementForTutorialLecture(tutorialLecture2);
        const expectedSidebarData: SidebarData = {
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: {
                allGroups: { entityData: [] },
                registeredGroups: { entityData: [expectedSidebarCardElement1] },
                furtherGroups: { entityData: [expectedSidebarCardElement2] },
                allTutorialLectures: { entityData: [] },
                currentTutorialLecture: { entityData: [expectedSidebarCardElement4] },
                furtherTutorialLectures: { entityData: [expectedSidebarCardElement3] },
            },
            ungroupedData: [expectedSidebarCardElement1, expectedSidebarCardElement2, expectedSidebarCardElement3, expectedSidebarCardElement4],
        };
        expect(component.sidebarData()).toEqual(expectedSidebarData);
    });

    it('should load groups and lectures if available to compute correct sidebar data', async () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ lectures: undefined, tutorialGroups: undefined });

        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialLecture(tutorialLecture1),
            getSidebarCardElementForTutorialLecture(tutorialLecture2),
        ]);
        vi.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        vi.spyOn(courseOverviewService, 'mapLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        const tutorialGroupFetchSpy = vi.spyOn(tutorialGroupApiServiceMock, 'getTutorialGroupsForCourse').mockReturnValue(of([tutorialGroup1, tutorialGroup2]));

        const tutorialLectureFetchSpy = vi
            .spyOn(lectureService, 'findAllTutorialLecturesByCourseId')
            .mockReturnValue(of(new HttpResponse({ body: [tutorialLecture1, tutorialLecture2] })));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupFetchSpy).toHaveBeenCalledOnce();
        expect(tutorialLectureFetchSpy).toHaveBeenCalledOnce();

        const expectedSidebarCardElement1: SidebarCardElement = getSidebarCardElementForTutorialGroup(tutorialGroup1);
        const expectedSidebarCardElement2: SidebarCardElement = getSidebarCardElementForTutorialGroup(tutorialGroup2);
        const expectedSidebarCardElement3: SidebarCardElement = getSidebarCardElementForTutorialLecture(tutorialLecture1);
        const expectedSidebarCardElement4: SidebarCardElement = getSidebarCardElementForTutorialLecture(tutorialLecture2);
        const expectedSidebarData: SidebarData = {
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: {
                allGroups: { entityData: [] },
                registeredGroups: { entityData: [expectedSidebarCardElement1] },
                furtherGroups: { entityData: [expectedSidebarCardElement2] },
                allTutorialLectures: { entityData: [] },
                currentTutorialLecture: { entityData: [expectedSidebarCardElement4] },
                furtherTutorialLectures: { entityData: [expectedSidebarCardElement3] },
            },
            ungroupedData: [expectedSidebarCardElement1, expectedSidebarCardElement2, expectedSidebarCardElement3, expectedSidebarCardElement4],
        };
        expect(component.sidebarData()).toEqual(expectedSidebarData);
    });

    it('should enrich the cached course with the fetched groups and lectures', async () => {
        const cachedCourse = { id: 1, lectures: undefined, tutorialGroups: undefined };
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(cachedCourse);
        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse').mockImplementation(() => {});
        vi.spyOn(tutorialGroupApiServiceMock, 'getTutorialGroupsForCourse').mockReturnValue(of(new HttpResponse({ body: [tutorialGroup1] })));
        vi.spyOn(lectureService, 'findAllTutorialLecturesByCourseId').mockReturnValue(of(new HttpResponse({ body: [tutorialLecture1] })));
        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(updateCourseSpy).toHaveBeenCalledWith(cachedCourse);
    });

    it('should clear the sidebar when a refresh returns no groups and no lectures', async () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [tutorialGroup1], lectures: [tutorialLecture1] });
        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([getSidebarCardElementForTutorialGroup(tutorialGroup1)]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([getSidebarCardElementForTutorialLecture(tutorialLecture1)]);
        vi.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        vi.spyOn(courseOverviewService, 'mapLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.sidebarData()?.ungroupedData).toHaveLength(2);

        // The group and the lecture are gone on the server; the refresh legitimately returns nothing. Keeping the old
        // cards would leave the student clicking entries that no longer exist.
        vi.spyOn(tutorialGroupApiServiceMock, 'getTutorialGroupsForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));
        vi.spyOn(lectureService, 'findAllTutorialLecturesByCourseId').mockReturnValue(of(new HttpResponse({ body: [] })));
        vi.mocked(courseOverviewService.mapTutorialGroupsToSidebarCardElements).mockReturnValue([]);
        vi.mocked(courseOverviewService.mapLecturesToSidebarCardElements).mockReturnValue([]);

        component.tutorialGroups.set([]);
        component.tutorialLectures.set([]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.sidebarData()?.ungroupedData).toHaveLength(0);
    });

    it('should drop deleted tutorial lectures from the stored course, so reopening the tab cannot resurrect them', async () => {
        const nonTutorialLecture = { id: 99, isTutorialLecture: false } as Lecture;
        const cachedCourse = { id: 1, tutorialGroups: [], lectures: [tutorialLecture1, nonTutorialLecture] };
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(cachedCourse);
        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse').mockImplementation(() => {});
        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([]);
        // The tutorial lecture was deleted on the server, so the refresh legitimately returns nothing
        vi.spyOn(lectureService, 'findAllTutorialLecturesByCourseId').mockReturnValue(of(new HttpResponse({ body: [] })));

        fixture.detectChanges();
        await fixture.whenStable();
        (component as any).loadAndSetTutorialLectures(1);
        await fixture.whenStable();

        expect(updateCourseSpy).toHaveBeenCalled();
        // Merging by id could not express the deletion: nothing in the empty response matched, so the deleted lecture
        // stayed cached and came straight back the next time the tab read it
        expect(cachedCourse.lectures).toEqual([nonTutorialLecture]);
    });

    it('should navigate to previously selected route', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [tutorialGroup1], lectures: [tutorialLecture1, tutorialLecture2] });
        vi.spyOn(sessionStorageService, 'retrieve').mockReturnValue('tutorial-lectures/7');
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        vi.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        vi.spyOn(courseOverviewService, 'mapLecturesToSidebarCardElements').mockReturnValue([getSidebarCardElementForTutorialLecture(tutorialLecture1)]);
        vi.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        vi.spyOn(courseOverviewService, 'mapLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['tutorial-lectures/7'], {
            relativeTo: component['activatedRoute'],
            replaceUrl: true,
        });
    });

    it('should toggle isCollapsed', () => {
        const initialCollapseState = component.isCollapsed();
        vi.spyOn(courseOverviewService, 'setSidebarCollapseState');
        component.toggleSidebar();
        expect(component.isCollapsed()).toBe(!initialCollapseState);
        expect(courseOverviewService.setSidebarCollapseState).toHaveBeenCalledWith('tutorialGroup', component.isCollapsed());
    });
});

function createTutorialGroup(id: number, title: string, isUserRegistered: boolean, isUserTutor: boolean): TutorialGroup {
    const tutorialGroup = new TutorialGroup();
    tutorialGroup.id = id;
    tutorialGroup.title = title;
    tutorialGroup.isUserRegistered = isUserRegistered;
    tutorialGroup.isUserTutor = isUserTutor;
    return tutorialGroup;
}

function createMockActivatedRoute() {
    return {
        parent: {
            paramMap: of(convertToParamMap({ courseId: '42' })),
        },
        firstChild: undefined,
    };
}

function createTutorialLecture(id: number, startDate: Dayjs, endDate: Dayjs): Lecture {
    const lecture = new Lecture();
    lecture.id = id;
    lecture.startDate = startDate;
    lecture.endDate = endDate;
    lecture.isTutorialLecture = true;
    return lecture;
}

function getSidebarCardElementForTutorialLecture(tutorialLecture: Lecture): SidebarCardElement {
    return {
        title: tutorialLecture.title!,
        id: tutorialLecture.id!,
        targetComponentSubRoute: 'tutorial-lectures',
        subtitleLeft: tutorialLecture.startDate!.format('MMM DD, YYYY'),
        size: 'M',
        startDate: tutorialLecture.startDate,
    };
}

function getSidebarCardElementForTutorialGroup(tutorialGroup: TutorialGroup): SidebarCardElement {
    return {
        title: tutorialGroup.title!,
        id: tutorialGroup.id!,
        size: 'M',
        subtitleLeft: 'No upcoming session',
        subtitleRight: undefined,
        attendanceText: '1 / 10',
        attendanceChipColor: 'var(--green)',
    };
}

@Component({ selector: 'jhi-sidebar', template: '' })
class MockSidebarComponent {
    itemSelected = input<boolean>();
    courseId = input<number>();
    sidebarData = input<SidebarData>();
    collapseState = input<CollapseState>();
    sidebarItemAlwaysShow = input<SidebarItemShowAlways>();
    pageTitle = input<string>();
    showSidebarToggle = input<boolean>();
    isSidebarCollapsed = input<boolean>();
}
