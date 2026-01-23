import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
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
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';

describe('CourseExercisesComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let courseStorageService: CourseStorageService;
    let exerciseService: ExerciseService;

    let course: Course;
    let exercise: Exercise;
    let _courseStorageStub: ReturnType<typeof vi.spyOn>;
    let exerciseServiceStub: ReturnType<typeof vi.spyOn>;

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const queryParamsSubject = new BehaviorSubject({ exercises: '', isMultiLaunch: 'false' });
    const route = {
        parent: parentRoute,
        queryParams: queryParamsSubject,
    } as any as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                RouterModule.forRoot([]),
                MockModule(ReactiveFormsModule),
                MockDirective(TranslateDirective),
                FaIconComponent,
                CourseExercisesComponent,
                SidebarComponent,
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(MockHasAnyAuthorityDirective),
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
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExercisesComponent);
        component = fixture.componentInstance;
        courseStorageService = TestBed.inject(CourseStorageService);
        exerciseService = TestBed.inject(ExerciseService);

        (component as any)._sidebarData.set({ groupByCategory: true, sidebarType: 'exercise', storageId: 'exercise' });
        course = new Course();
        course.id = 123;
        exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
        exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
        course.exercises = [exercise];
        vi.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(course));
        _courseStorageStub = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        exerciseServiceStub = vi.spyOn(exerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: exercise,
                }),
            ),
        );

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        TestBed.tick();
        expect(component.course()).toEqual(course);
        // Component should be properly initialized with the course
        expect(component.courseId()).toBe(course.id);
    });

    it('should display sidebar when course is provided', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        TestBed.tick();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('jhi-sidebar')).not.toBeNull();
    });

    it('should toggle sidebar visibility based on isCollapsed property', () => {
        // Ensure course is set and LTI is not shown
        (component as any)._course.set(course);
        (component as any)._isShownViaLti.set(false);
        (component as any)._isCollapsed.set(true);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();

        (component as any)._isCollapsed.set(false);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        (component as any)._isCollapsed.set(false);
        TestBed.tick();

        component.toggleSidebar();
        TestBed.tick();
        expect(component.isCollapsed).toBe(true);

        component.toggleSidebar();
        TestBed.tick();
        expect(component.isCollapsed).toBe(false);
    });

    it('should display "Please Select an Exercise" when no exercise is selected', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        (component as any)._exerciseSelected.set(false);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        const noExerciseElement = fixture.debugElement.query(By.css('[jhiTranslate$=selectExercise]'));
        expect(noExerciseElement).toBeTruthy();
        expect(noExerciseElement.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.courseOverview.exerciseDetails.selectExercise');
    });

    it('should display the exercise details when an exercise is selected', () => {
        // Ensure course is set
        (component as any)._course.set(course);
        (component as any)._exerciseSelected.set(true);
        TestBed.tick();
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
    });

    it('should call exerciseService if multiLaunchExercises are present', () => {
        (component as any)._isMultiLaunch.set(true);
        (component as any)._multiLaunchExerciseIDs.set([1, 2]);

        component.prepareSidebarData();

        expect(exerciseServiceStub).toHaveBeenCalledTimes(2);
        expect(exerciseServiceStub).toHaveBeenCalledWith(1);
        expect(exerciseServiceStub).toHaveBeenCalledWith(2);
    });
});
