import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
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

    it('should render the included-in-score badge for an ordinary optional exercise', () => {
        fixture.componentRef.setInput('exercise', { ...exercise, includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED } as Exercise);
        fixture.componentRef.setInput('showTags', { includedInScore: true });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('jhi-included-in-score-badge')).not.toBeNull();
    });
});
