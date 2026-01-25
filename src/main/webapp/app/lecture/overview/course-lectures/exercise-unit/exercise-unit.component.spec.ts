import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { By } from '@angular/platform-browser';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { MockComponent } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ExerciseUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let exerciseUnit: ExerciseUnit;
    let course: Course;

    let exerciseUnitComponentFixture: ComponentFixture<ExerciseUnitComponent>;
    let exerciseUnitComponent: ExerciseUnitComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseUnitComponent, MockComponent(CourseExerciseRowComponent)],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: ProfileService, useClass: MockProfileService }],
        }).compileComponents();

        exerciseUnitComponentFixture = TestBed.createComponent(ExerciseUnitComponent);
        exerciseUnitComponent = exerciseUnitComponentFixture.componentInstance;

        course = new Course();
        course.id = 1;
        exerciseUnit = new ExerciseUnit();
        exerciseUnit.exercise = new TextExercise(course, undefined);
        exerciseUnit.exercise.id = 1;

        exerciseUnitComponentFixture.componentRef.setInput('exerciseUnit', exerciseUnit);
        exerciseUnitComponentFixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and set input of course exercise row correctly', () => {
        exerciseUnitComponentFixture.detectChanges();
        const courseExerciseRowComponent: CourseExerciseRowComponent = exerciseUnitComponentFixture.debugElement.query(By.directive(CourseExerciseRowComponent)).componentInstance;

        expect(courseExerciseRowComponent.course()).toEqual(exerciseUnitComponent.course());
        expect(courseExerciseRowComponent.exercise()).toEqual(exerciseUnitComponent.exerciseUnit().exercise);
    });
});
