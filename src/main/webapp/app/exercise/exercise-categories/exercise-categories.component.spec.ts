import { ComponentFixture, TestBed } from '@angular/core/testing';
import { isSignal } from '@angular/core';
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
        expect(isSignal(component.exercise)).toBe(true);
        expect(component.exercise()).toBe(exercise);
    });

    it('should render exercise categories from signal inputs', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Algorithms');
    });

    it('should use white text on a dark category color and black text on a light one', () => {
        expect(component.categoryTextColor('#8b0000')).toBe('white');
        expect(component.categoryTextColor('#ffff00')).toBe('black');
        // White on pure red only reaches about 4:1, black reaches about 5:1
        expect(component.categoryTextColor('#ff0000')).toBe('black');
        // Values the browser may not apply as a background keep the inherited text color
        expect(component.categoryTextColor('000000')).toBeUndefined();
        expect(component.categoryTextColor('#00000000')).toBeUndefined();
        expect(component.categoryTextColor(undefined)).toBeUndefined();
    });

    it('should apply the contrasting text color to the rendered category', () => {
        fixture.componentRef.setInput('exercise', { ...exercise, categories: [{ category: 'Dark', color: '#8b0000' }] } as Exercise);
        fixture.detectChanges();

        const badge = fixture.nativeElement.querySelector('span.rounded') as HTMLElement;
        expect(badge.style.color).toBe('white');
    });
});
