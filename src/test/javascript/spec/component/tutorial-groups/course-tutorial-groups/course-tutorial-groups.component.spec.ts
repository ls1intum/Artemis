import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BehaviorSubject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { runOnPushChangeDetection } from '../../../helpers/on-push-change-detection.helper';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

@Component({
    selector: 'jhi-course-tutorial-groups-overview',
    template: '<div id="tutorialGroupsOverview">Hello World :)</div>',
})
class MockCourseTutorialGroupsOverviewComponent {
    @Input()
    course: Course;
    @Input()
    tutorialGroups: TutorialGroup[] = [];
    @Input()
    configuration?: TutorialGroupsConfiguration;
}

@Component({
    selector: 'jhi-course-tutorial-groups-registered',
    template: '<div id="registeredTutorialGroups">Hello World ;)</div>',
})
class MockCourseTutorialGroupsRegisteredComponent {
    @Input()
    registeredTutorialGroups: TutorialGroup[] = [];
    @Input()
    course: Course;

    @Input()
    configuration?: TutorialGroupsConfiguration;
}

describe('CourseTutorialGroupsComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;
    let component: CourseTutorialGroupsComponent;

    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupThree: TutorialGroup;

    const router = new MockRouter();

    let queryParamsSubject: BehaviorSubject<Params>;

    beforeEach(() => {
        router.navigate.mockImplementation(() => Promise.resolve(true));

        queryParamsSubject = new BehaviorSubject(convertToParamMap({}));
        TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupsComponent, MockCourseTutorialGroupsOverviewComponent, MockCourseTutorialGroupsRegisteredComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseStorageService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: new BehaviorSubject(
                                    convertToParamMap({
                                        courseId: 1,
                                    }),
                                ),
                            },
                        },
                        queryParams: queryParamsSubject,
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseTutorialGroupsComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1, isUserTutor: true });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, isUserRegistered: true });
                tutorialGroupThree = generateExampleTutorialGroup({ id: 3 });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should load tutorial groups from service if they are not available in the cache and update the cache', () => {
        const tutorialGroupsService = TestBed.inject(TutorialGroupsService);
        const getAllOfCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [tutorialGroupOne, tutorialGroupTwo],
                    status: 200,
                }),
            ),
        );
        const mockCourse = { id: 1, title: 'Test Course' } as Course;
        const getCourseSpy = jest.spyOn(TestBed.inject(CourseStorageService), 'getCourse').mockReturnValue(mockCourse);
        const updateCourseSpy = jest.spyOn(TestBed.inject(CourseStorageService), 'updateCourse');
        fixture.detectChanges();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(component.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        // one to get the course, one to get the tutorial groups, one to perform the update
        expect(getCourseSpy).toHaveBeenCalledTimes(3);
        expect(getCourseSpy).toHaveBeenCalledWith(1);
        // check that the cache was updated
        expect(mockCourse.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(updateCourseSpy).toHaveBeenCalledOnce();
        expect(updateCourseSpy).toHaveBeenCalledWith(mockCourse);
    });

    it('should not load tutorial groups from service if they are available in the cache', () => {
        const tutorialGroupsService = TestBed.inject(TutorialGroupsService);
        const getAllOfCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllForCourse');
        const getCourseSpy = jest
            .spyOn(TestBed.inject(CourseStorageService), 'getCourse')
            .mockReturnValue({ id: 1, title: 'Test Course', tutorialGroups: [tutorialGroupOne, tutorialGroupTwo] } as Course);
        const updateCourseSpy = jest.spyOn(TestBed.inject(CourseStorageService), 'updateCourse');

        fixture.detectChanges();
        expect(getCourseSpy).toHaveBeenCalledTimes(2);
        expect(getCourseSpy).toHaveBeenCalledWith(1);
        expect(component.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(getAllOfCourseSpy).not.toHaveBeenCalled();
        expect(updateCourseSpy).not.toHaveBeenCalled();
    });

    it('should set the filter depending on the query param', () => {
        fixture.detectChanges();
        queryParamsSubject.next({ filter: 'all' });
        expect(component.selectedFilter).toBe('all');
        queryParamsSubject.next({ filter: 'registered' });
        runOnPushChangeDetection(fixture);
        expect(component.selectedFilter).toBe('registered');
    });

    it('should set the query params when a different filter is selected', () => {
        fixture.detectChanges();
        component.onFilterChange('all');
        const activatedRoute = TestBed.inject(ActivatedRoute);
        const navigateSpy = jest.spyOn(router, 'navigate');
        expect(navigateSpy).toHaveBeenCalledWith([], {
            relativeTo: activatedRoute,
            queryParams: { filter: 'all' },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
    });

    it('should filter registered tutorial groups for student', () => {
        component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo, tutorialGroupThree];
        component.course = { id: 1, title: 'Test Course' } as Course;
        expect(component.registeredTutorialGroups).toEqual([tutorialGroupTwo]);
    });

    it('should filter registered tutorial groups for tutor', () => {
        component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo, tutorialGroupThree];
        component.course = { id: 1, title: 'Test Course', isAtLeastTutor: true } as Course;
        expect(component.registeredTutorialGroups).toEqual([tutorialGroupOne]);
    });
});
