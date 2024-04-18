import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';

describe('CourseExercisesComponent', () => {
    let fixture: ComponentFixture<CourseExercisesComponent>;
    let component: CourseExercisesComponent;
    let courseStorageService: CourseStorageService;

    let course: Course;
    let exercise: Exercise;
    let courseStorageStub: jest.SpyInstance;

    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, RouterTestingModule.withRoutes([]), MockModule(ReactiveFormsModule)],
            declarations: [
                CourseExercisesComponent,
                SidebarComponent,
                SearchFilterComponent,
                MockDirective(OrionFilterDirective),
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
            ],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExercisesComponent);
                component = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);

                component.sidebarData = { groupByCategory: true, sidebarType: 'exercise', storageId: 'exercise' };
                course = new Course();
                course.id = 123;
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];
                jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(course));
                courseStorageStub = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);

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
        // Wait for any async operations to complete here if necessary
        fixture.detectChanges(); // Trigger change detection again if async operations might change the state
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
        expect(fixture.nativeElement.textContent).toContain('Please Select an Exercise');
    });

    it('should display the exercise details when an exercise is selected', () => {
        component.exerciseSelected = true;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
    });
});
