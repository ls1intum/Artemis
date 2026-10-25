import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ResultService } from 'app/exercise/result/result.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStatisticsResultsComponent } from './course-statistics-results.component';

describe('CourseStatisticsResultsComponent', () => {
    let fixture: ComponentFixture<CourseStatisticsResultsComponent>;
    const formatResult = vi.fn().mockReturnValue('0%, 0 points');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseStatisticsResultsComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: ResultService, useValue: { getResultString: formatResult } }],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseStatisticsResultsComponent);
        fixture.componentRef.setInput('courseId', 589);
        fixture.componentRef.setInput('exercises', []);
        fixture.detectChanges();
    });

    it('paginates 80 exercises and keeps every displayed title linked', () => {
        const exercises = Array.from({ length: 80 }, (_, i) => ({ id: i + 1, title: `Exercise ${i + 1}`, type: ExerciseType.PROGRAMMING }) as Exercise);
        fixture.componentRef.setInput('exercises', exercises);
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows()).toHaveLength(20);
        const links = fixture.nativeElement.querySelectorAll('[data-testid="statistics-exercise-link"]');
        expect(links).toHaveLength(20);
        expect(links[0].getAttribute('href')).toBe('/courses/589/exercises/1');
        fixture.componentInstance.page.set(3);
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows()[0].exercise.id).toBe(61);
    });

    it('searches the whole result set and resets pagination without a dropdown', () => {
        fixture.componentRef.setInput(
            'exercises',
            Array.from({ length: 80 }, (_, i) => ({ id: i + 1, title: `Exercise ${i + 1}`, type: ExerciseType.PROGRAMMING }) as Exercise),
        );
        fixture.detectChanges();
        fixture.componentInstance.page.set(3);
        fixture.componentInstance.search.set('Exercise 80');
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([80]);
        expect(fixture.nativeElement.querySelector('select')).toBeNull();
    });

    it('preserves an assessed zero and uses the standard result formatter', () => {
        const exercise = {
            id: 1,
            type: ExerciseType.TEXT,
            maxPoints: 10,
            studentParticipations: [{ id: 2, submissions: [{ submitted: true, results: [{ id: 3, rated: true, score: 0 }] }] }],
        } as Exercise;
        fixture.componentRef.setInput('exercises', [exercise]);
        fixture.detectChanges();
        expect(fixture.componentInstance.rows()[0].state).toBe('graded');
        expect(formatResult).toHaveBeenCalledWith(expect.objectContaining({ score: 0 }), exercise, expect.objectContaining({ id: 2 }), true);
        expect(fixture.nativeElement.textContent).toContain('0%, 0 points');
    });

    it('distinguishes pending submissions from no submission and filters them visibly', () => {
        fixture.componentRef.setInput('exercises', [
            { id: 1, type: ExerciseType.TEXT, title: 'Pending', studentParticipations: [{ hasSubmitted: true, submissions: [] }] },
            { id: 2, type: ExerciseType.TEXT, title: 'Not submitted' },
        ] as Exercise[]);
        fixture.detectChanges();
        expect(fixture.componentInstance.rows().map((row) => row.state)).toEqual(['pending', 'notSubmitted']);
        fixture.componentInstance.status.set('pending');
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([1]);
    });

    it('shows scoring inclusion and a variant-group cap alongside the standard result', () => {
        fixture.componentRef.setInput('exercises', [
            { id: 1, title: 'Regular', includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY },
            { id: 2, title: 'Bonus', includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS },
            { id: 3, title: 'Practice', includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED },
            { id: 4, title: 'Variant', exerciseVariantGroup: { id: 1, title: 'Alternatives', maxPoints: 8 } },
        ] as Exercise[]);
        fixture.detectChanges();
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('artemisApp.courseOverview.statistics.redesign.scoring.included');
        expect(text).toContain('artemisApp.courseOverview.statistics.redesign.scoring.bonus');
        expect(text).toContain('artemisApp.courseOverview.statistics.redesign.scoring.excluded');
        expect(fixture.nativeElement.querySelector('[data-testid="statistics-variant-cap"]')).not.toBeNull();
    });
});
