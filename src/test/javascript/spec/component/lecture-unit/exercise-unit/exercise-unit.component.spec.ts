import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-course-exercise-row', template: '' })
class CourseExerciseRowStubComponent {
    @Input()
    exercise: Exercise;
    @Input()
    course: Course;
    @Input()
    hasGuidedTour: boolean;
    @Input()
    isPresentationMode: boolean;
}

describe('ExerciseUnitComponent', () => {
    let exerciseUnit: ExerciseUnit;
    let course: Course;

    let exerciseUnitComponentFixture: ComponentFixture<ExerciseUnitComponent>;
    let exerciseUnitComponent: ExerciseUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ExerciseUnitComponent, CourseExerciseRowStubComponent],
            providers: [],
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
        const courseExerciseRowStubComponent: CourseExerciseRowStubComponent = exerciseUnitComponentFixture.debugElement.query(
            By.directive(CourseExerciseRowStubComponent),
        ).componentInstance;

        expect(courseExerciseRowStubComponent.course).toEqual(exerciseUnitComponent.course);
        expect(courseExerciseRowStubComponent.exercise).toEqual(exerciseUnitComponent.exerciseUnit.exercise);
    });
});
