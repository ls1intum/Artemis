import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('DifficultyBadge', () => {
    let component: DifficultyBadgeComponent;

    const exercise = new ProgrammingExercise(undefined, undefined);

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        const fixture = TestBed.createComponent(DifficultyBadgeComponent);
        component = fixture.componentInstance;

        component.exercise = exercise;
        component.showNoLevel = false;
    });

    it('should show an info badge if no difficulty level should be shown', () => {
        component.showNoLevel = true;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-info');
    });

    it('should show no badge if the exercise has no difficulty level', () => {
        component.showNoLevel = false;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBeUndefined();
    });

    it('should a success badge for easy exercises', () => {
        exercise.difficulty = DifficultyLevel.EASY;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-success');
    });

    it('should a warning badge for medium difficulty exercises', () => {
        exercise.difficulty = DifficultyLevel.MEDIUM;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-warning');
    });

    it('should a danger badge for hard exercises', () => {
        exercise.difficulty = DifficultyLevel.HARD;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-danger');
    });
});
