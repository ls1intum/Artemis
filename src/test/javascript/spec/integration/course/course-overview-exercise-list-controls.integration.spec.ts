import { CourseExercisesComponent, ExerciseFilter } from 'app/overview/course-exercises/course-exercises.component';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ArtemisTestModule } from '../../test.module';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { RouterTestingModule } from '@angular/router/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';

describe('CourseOverviewExerciseListControls', () => {
    let parentFixture: ComponentFixture<CourseOverviewComponent>;
    let parentComponent: CourseOverviewComponent;
    let childFixture: ComponentFixture<CourseExercisesComponent>;
    let childComponent: CourseExercisesComponent;

    let courseCalculation: CourseScoreCalculationService;

    let course: Course;

    const parentRoute = { params: of({ courseId: '123' }) } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseId: '123' }) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseOverviewComponent,
                CourseExercisesComponent,
                MockDirective(OrionFilterDirective),
                MockComponent(SidePanelComponent),
                TranslatePipeMock,
                MockPipe(ArtemisDatePipe),
                MockTranslateValuesDirective,
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .compileComponents()
            .then(() => {
                parentFixture = TestBed.createComponent(CourseOverviewComponent);
                parentComponent = parentFixture.componentInstance;

                childFixture = TestBed.createComponent(CourseExercisesComponent);
                childComponent = childFixture.componentInstance;

                courseCalculation = TestBed.inject(CourseScoreCalculationService);

                course = new Course();
                course.id = 123;
                const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
                exercise.dueDate = dayjs('2021-01-13T16:11:00+01:00').add(1, 'days');
                exercise.releaseDate = dayjs('2021-01-13T16:11:00+01:00').subtract(1, 'days');
                course.exercises = [exercise];

                jest.spyOn(courseCalculation, 'getCourse').mockReturnValue(course);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show the current amount of filters in the filter button and change background color', () => {
        parentComponent.onSubRouteActivate(childComponent);
        childFixture.detectChanges();
        parentFixture.detectChanges();

        const filterDropdown = parentFixture.debugElement.query(By.css('#filter-dropdown-button'));
        expect(filterDropdown).not.toBeNull();

        const filterDropdownLabel = filterDropdown.query(By.css('span'));
        expect(filterDropdownLabel).not.toBeNull();

        // Start: No filters should bet set
        childComponent.activeFilters.clear();
        childFixture.detectChanges();
        parentFixture.detectChanges();

        expect(filterDropdown.nativeElement.classList).toContain('btn-secondary');
        expect(filterDropdownLabel.nativeElement.textContent).toBe('artemisApp.courseOverview.exerciseList.filter: [{"num":0}]');

        // Set a few filters
        childComponent.toggleFilters([ExerciseFilter.OVERDUE, ExerciseFilter.NEEDS_WORK, ExerciseFilter.OPTIONAL]);
        childFixture.detectChanges();
        parentFixture.detectChanges();

        expect(filterDropdown.nativeElement.classList).toContain('btn-success');
        expect(filterDropdownLabel.nativeElement.textContent).toBe('artemisApp.courseOverview.exerciseList.filter: [{"num":3}]');
    });
});
