import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/shared/course-tutorial-groups/course-tutorial-groups.component';
import { MockDirective, MockProvider } from 'ng-mocks';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { convertToParamMap } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import dayjs, { Dayjs } from 'dayjs/esm';
import { SidebarCardElement, SidebarData } from 'app/shared/types/sidebar';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { HttpResponse } from '@angular/common/http';

describe('CourseTutorialGroupsComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;
    let component: CourseTutorialGroupsComponent;

    let courseOverviewService: CourseOverviewService;
    let tutorialGroupService: TutorialGroupsService;
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
        await TestBed.configureTestingModule({
            declarations: [MockDirective(TranslateDirective)],
            imports: [CourseTutorialGroupsComponent, MockSidebarComponent],
            providers: [
                { provide: Router, useValue: mockRouter },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                MockProvider(AlertService),
                MockProvider(CourseStorageService),
                MockProvider(TutorialGroupsService),
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
        tutorialGroupService = TestBed.inject(TutorialGroupsService);
        courseStorageService = TestBed.inject(CourseStorageService);
        lectureService = TestBed.inject(LectureService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should use cached groups and lectures if available to compute correct sidebar data', async () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [tutorialGroup1, tutorialGroup2], lectures: [tutorialLecture1, tutorialLecture2] });

        jest.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        jest.spyOn(courseOverviewService, 'mapTutorialLecturesToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialLecture(tutorialLecture1),
            getSidebarCardElementForTutorialLecture(tutorialLecture2),
        ]);
        jest.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        jest.spyOn(courseOverviewService, 'mapTutorialLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        const tutorialGroupFetchSpy = jest.spyOn(tutorialGroupService, 'getAllForCourse');
        const tutorialLectureFetchSpy = jest.spyOn(lectureService, 'findAllTutorialLecturesByCourseId');

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
                registeredGroups: { entityData: [expectedSidebarCardElement1] },
                furtherGroups: { entityData: [expectedSidebarCardElement2] },
                allGroups: { entityData: [] },
                currentTutorialLecture: { entityData: [expectedSidebarCardElement4] },
                furtherTutorialLectures: { entityData: [expectedSidebarCardElement3] },
            },
            ungroupedData: [expectedSidebarCardElement1, expectedSidebarCardElement2, expectedSidebarCardElement3, expectedSidebarCardElement4],
        };
        expect(component.sidebarData()).toEqual(expectedSidebarData);
    });

    it('should load groups and lectures if available to compute correct sidebar data', async () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);

        jest.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        jest.spyOn(courseOverviewService, 'mapTutorialLecturesToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialLecture(tutorialLecture1),
            getSidebarCardElementForTutorialLecture(tutorialLecture2),
        ]);
        jest.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        jest.spyOn(courseOverviewService, 'mapTutorialLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        const tutorialGroupFetchSpy = jest.spyOn(tutorialGroupService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [tutorialGroup1, tutorialGroup2] })));

        const tutorialLectureFetchSpy = jest
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
                registeredGroups: { entityData: [expectedSidebarCardElement1] },
                furtherGroups: { entityData: [expectedSidebarCardElement2] },
                allGroups: { entityData: [] },
                currentTutorialLecture: { entityData: [expectedSidebarCardElement4] },
                furtherTutorialLectures: { entityData: [expectedSidebarCardElement3] },
            },
            ungroupedData: [expectedSidebarCardElement1, expectedSidebarCardElement2, expectedSidebarCardElement3, expectedSidebarCardElement4],
        };
        expect(component.sidebarData()).toEqual(expectedSidebarData);
    });

    it('should navigate to previously selected route', () => {
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({ tutorialGroups: [tutorialGroup1], lectures: [tutorialLecture1, tutorialLecture2] });
        jest.spyOn(sessionStorageService, 'retrieve').mockReturnValue('tutorial-lectures/7');
        const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

        jest.spyOn(courseOverviewService, 'mapTutorialGroupsToSidebarCardElements').mockReturnValue([
            getSidebarCardElementForTutorialGroup(tutorialGroup1),
            getSidebarCardElementForTutorialGroup(tutorialGroup2),
        ]);
        jest.spyOn(courseOverviewService, 'mapTutorialLecturesToSidebarCardElements').mockReturnValue([getSidebarCardElementForTutorialLecture(tutorialLecture1)]);
        jest.spyOn(courseOverviewService, 'mapTutorialGroupToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialGroup);
        jest.spyOn(courseOverviewService, 'mapTutorialLectureToSidebarCardElement').mockImplementation(getSidebarCardElementForTutorialLecture);

        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['tutorial-lectures/7'], {
            relativeTo: component['activatedRoute'],
            replaceUrl: true,
        });
        expect(component.itemSelected()).toBeTrue();
    });

    it('should toggle isCollapsed', () => {
        const initialCollapseState = component.isCollapsed;
        jest.spyOn(courseOverviewService, 'setSidebarCollapseState');
        component.toggleSidebar();
        expect(component.isCollapsed).toBe(!initialCollapseState);
        expect(courseOverviewService.setSidebarCollapseState).toHaveBeenCalledWith('tutorialGroup', component.isCollapsed);
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
        firstChild: {
            snapshot: {
                params: {
                    tutorialGroupId: undefined,
                    lectureId: undefined,
                },
            },
        },
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
    @Input() itemSelected: any;
    @Input() courseId: any;
    @Input() sidebarData: any;
    @Input() collapseState: any;
    @Input() sidebarItemAlwaysShow: any;
}
