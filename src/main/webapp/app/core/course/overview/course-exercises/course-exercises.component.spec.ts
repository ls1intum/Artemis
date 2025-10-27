import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { CourseExercisesComponent } from 'app/core/course/overview/course-exercises/course-exercises.component';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CourseExercisesComponent', () => {
    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let courseStorageService: CourseStorageService;
    let exerciseService: ExerciseService;

    let course: Course;
    let exercise: Exercise;
    let courseStorageStub: jest.SpyInstance;
    let exerciseServiceStub: jest.SpyInstance;

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const queryParamsSubject = new BehaviorSubject({ exercises: '', isMultiLaunch: 'false' });
    const route = {
        parent: parentRoute,
        queryParams: queryParamsSubject,
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, RouterModule.forRoot([]), MockModule(ReactiveFormsModule), MockDirective(TranslateDirective), FaIconComponent],
            declarations: [
                CourseExercisesComponent,
                SidebarComponent,
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(SortByDirective),
                TranslatePipeMock,
                MockDirective(SortDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockTranslateValuesDirective,
                MockPipe(SearchFilterPipe),
                MockComponent(SearchFilterComponent),
            ],
            providers: [
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesComponent);
                component = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);
                exerciseService = TestBed.inject(ExerciseService);

                component.sidebarData = { groupByCategory: true, sidebarType: 'exercise', storageId: 'exercise' };
                course = new Course();
                course.id = 123;
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];
                jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(course));
                courseStorageStub = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
                exerciseServiceStub = jest.spyOn(exerciseService, 'find').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: exercise,
                        }),
                    ),
                );

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component.course).toEqual(course);
        expect(courseStorageStub.mock.calls).toHaveLength(1);
        expect(courseStorageStub.mock.calls[0][0]).toBe(course.id);
        component.ngOnDestroy();
    });

    it('should display sidebar when course is provided', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('jhi-sidebar')).not.toBeNull();
    });

    it('should toggle sidebar visibility based on isCollapsed property', () => {
        component.isCollapsed = true;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();

        component.isCollapsed = false;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.toggleSidebar();
        expect(component.isCollapsed).toBeTrue();

        component.toggleSidebar();
        expect(component.isCollapsed).toBeFalse();
    });

    it('should display "Please Select an Exercise" when no exercise is selected', () => {
        component.exerciseSelected = false;
        fixture.detectChanges();
        const noExerciseElement = fixture.debugElement.query(By.css('[jhiTranslate$=selectExercise]'));
        expect(noExerciseElement).toBeTruthy();
        expect(noExerciseElement.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.courseOverview.exerciseDetails.selectExercise');
    });

    it('should display the exercise details when an exercise is selected', () => {
        component.exerciseSelected = true;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
    });

    it('should call exerciseService if multiLaunchExercises are present', () => {
        component.isMultiLaunch = true;
        component.multiLaunchExerciseIDs = [1, 2];

        component.prepareSidebarData();

        expect(exerciseServiceStub).toHaveBeenCalledTimes(2);
        expect(exerciseServiceStub).toHaveBeenCalledWith(1);
        expect(exerciseServiceStub).toHaveBeenCalledWith(2);
    });
});
