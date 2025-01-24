import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { By } from '@angular/platform-browser';
import { CourseExerciseRowComponent } from '../../../../../../main/webapp/app/overview/course-exercises/course-exercise-row.component';
import { MockComponent } from 'ng-mocks';

describe('ExerciseUnitComponent', () => {
    let exerciseUnit: ExerciseUnit;
    let course: Course;

    let exerciseUnitComponentFixture: ComponentFixture<ExerciseUnitComponent>;
    let exerciseUnitComponent: ExerciseUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExerciseUnitComponent, MockComponent(CourseExerciseRowComponent)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                exerciseUnitComponentFixture = TestBed.createComponent(ExerciseUnitComponent);
                exerciseUnitComponent = exerciseUnitComponentFixture.componentInstance;

                course = new Course();
                exerciseUnit = new ExerciseUnit();
                exerciseUnit.exercise = new TextExercise(course, undefined);

                exerciseUnitComponent.exerciseUnit = exerciseUnit;
                exerciseUnitComponent.course = course;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        jest.resetModules();
    });

    it('should initialize and set input of course exercise row correctly', () => {
        exerciseUnitComponentFixture.detectChanges();
        const courseExerciseRowComponent: CourseExerciseRowComponent = exerciseUnitComponentFixture.debugElement.query(By.directive(CourseExerciseRowComponent)).componentInstance;

        expect(courseExerciseRowComponent.course).toEqual(exerciseUnitComponent.course);
        expect(courseExerciseRowComponent.exercise).toEqual(exerciseUnitComponent.exerciseUnit.exercise);
    });
});
