import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DifficultyLevel, Exercise, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const exercise = {
    id: 1,
    title: 'Sample Exercise',
    difficulty: DifficultyLevel.EASY,
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    categories: [{ category: 'Algorithms', color: '#ff0000' }],
    course: { id: 2 },
} as Exercise;

describe('ExerciseCategoriesComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExerciseCategoriesComponent;
    let fixture: ComponentFixture<ExerciseCategoriesComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseCategoriesComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseCategoriesComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
    });

    it('should expose exercise as a signal input', () => {
        expect(typeof component.exercise).toBe('function');
        expect(component.exercise()).toBe(exercise);
    });

    it('should render exercise categories from signal inputs', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Algorithms');
    });
});
