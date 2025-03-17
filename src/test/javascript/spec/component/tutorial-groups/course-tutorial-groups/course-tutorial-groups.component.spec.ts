import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, convertToParamMap, Router, RouterModule } from '@angular/router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { CourseTutorialGroupsComponent } from 'app/course/overview/course-tutorial-groups/course-tutorial-groups.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BehaviorSubject, of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/course-overview.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseTutorialGroupsComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsComponent>;
    let component: CourseTutorialGroupsComponent;

    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;
    let courseOverviewService: CourseOverviewService;

    const router = new MockRouter();

    beforeEach(() => {
        router.navigate.mockImplementation(() => Promise.resolve(true));

        TestBed.configureTestingModule({
            imports: [RouterModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(TranslateDirective)],
            declarations: [CourseTutorialGroupsComponent, MockPipe(ArtemisTranslatePipe), SidebarComponent, MockComponent(SearchFilterComponent), MockPipe(SearchFilterPipe)],
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
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseTutorialGroupsComponent);
                component = fixture.componentInstance;
                courseOverviewService = TestBed.inject(CourseOverviewService);
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

    it('should toggle isCollapsed and call setSidebarCollapseState with the correct arguments', () => {
        const initialCollapseState = component.isCollapsed;
        const detectChangesSpy = jest.spyOn(component['cdr'], 'detectChanges');
        jest.spyOn(courseOverviewService, 'setSidebarCollapseState');
        component.toggleSidebar();
        expect(component.isCollapsed).toBe(!initialCollapseState);
        expect(courseOverviewService.setSidebarCollapseState).toHaveBeenCalledWith('tutorialGroup', component.isCollapsed);
        expect(detectChangesSpy).toHaveBeenCalled();
    });
});
