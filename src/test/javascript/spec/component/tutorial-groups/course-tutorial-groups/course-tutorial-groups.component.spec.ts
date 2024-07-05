import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router, RouterModule, convertToParamMap } from '@angular/router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { CourseTutorialGroupsComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BehaviorSubject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ArtemisTestModule } from '../../../test.module';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CourseTutorialGroupsComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;
    let component: CourseTutorialGroupsComponent;

    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        router.navigate.mockImplementation(() => Promise.resolve(true));

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(TranslateDirective)],
            declarations: [CourseTutorialGroupsComponent, MockPipe(ArtemisTranslatePipe), SidebarComponent, SearchFilterComponent, MockPipe(SearchFilterPipe)],
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
                            paramMap: new BehaviorSubject(
                                convertToParamMap({
                                    courseId: 1,
                                }),
                            ),
                        },
                        params: of({ tutorialGroupId: 5 }),
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseTutorialGroupsComponent);
                component = fixture.componentInstance;
                component.sidebarData = { groupByCategory: true, sidebarType: 'default', storageId: 'tutorialGroup' };
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1, isUserTutor: true });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, isUserRegistered: true });
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
});
