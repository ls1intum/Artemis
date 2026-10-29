import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ResultService } from 'app/exercise/result/result.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStatisticsResultsComponent } from './course-statistics-results.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

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

    it('orders the ledger chronologically with undated exercises last without mutating the supplied exercises', () => {
        const exercises = [
            { id: 4, title: 'Undated' },
            { id: 3, title: 'Later', dueDate: dayjs('2026-10-15') },
            { id: 2, title: 'Earlier, second', dueDate: dayjs('2026-10-01') },
            { id: 1, title: 'Earlier, first', dueDate: dayjs('2026-10-01') },
        ] as Exercise[];
        fixture.componentRef.setInput('exercises', exercises);
        fixture.detectChanges();
        expect(fixture.componentInstance.rows().map((row) => row.exercise.id)).toEqual([1, 2, 3, 4]);
        expect(exercises.map((exercise) => exercise.id)).toEqual([4, 3, 2, 1]);
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

    it.each([
        {
            name: 'successful compilation without visible tests',
            assessmentType: AssessmentType.AUTOMATIC,
            score: 0,
            testCaseCount: 0,
            buildFailed: false,
            icon: faCheckCircle,
            color: 'var(--tumaet-ui-state-success-foreground)',
        },
        {
            name: 'a preliminary automatic result',
            assessmentType: AssessmentType.SEMI_AUTOMATIC,
            score: 80,
            testCaseCount: 1,
            buildFailed: false,
            icon: faQuestionCircle,
            color: 'var(--tumaet-ui-muted-color)',
        },
        {
            name: 'a compilation failure',
            assessmentType: AssessmentType.AUTOMATIC,
            score: 0,
            testCaseCount: 0,
            buildFailed: true,
            icon: faTimesCircle,
            color: 'var(--tumaet-ui-state-danger-foreground)',
        },
        {
            name: 'an assessed zero with visible tests',
            assessmentType: AssessmentType.AUTOMATIC,
            score: 0,
            testCaseCount: 1,
            buildFailed: false,
            icon: faTimesCircle,
            color: 'var(--tumaet-ui-state-danger-foreground)',
        },
    ])('uses standard programming styling for $name without an embedded exercise', ({ assessmentType, score, testCaseCount, buildFailed, icon, color }) => {
        const result = { id: 3, rated: true, score, testCaseCount, successful: !buildFailed, assessmentType: AssessmentType.AUTOMATIC, completionDate: dayjs('2026-01-01') };
        const submission = { id: 4, submitted: true, submissionExerciseType: SubmissionExerciseType.PROGRAMMING, buildFailed, results: [result] };
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        exercise.maxPoints = 10;
        exercise.assessmentType = assessmentType;
        exercise.studentParticipations = [{ id: 2, type: ParticipationType.PROGRAMMING, submissions: [submission] }];
        const course = { id: 589, accuracyOfScores: 1 };
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercises', [exercise]);
        fixture.detectChanges();

        expect(exercise.studentParticipations![0].exercise).toBeUndefined();
        expect(exercise.course).toBeUndefined();
        const resultLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('tbody tr td:nth-child(2) a');
        expect(resultLink?.getAttribute('href')).toBe('/courses/589/exercises/1');
        expect(resultLink?.textContent).toContain('0%, 0 points');
        const renderedIcon = resultLink?.querySelector<HTMLElement>('fa-icon');
        expect(renderedIcon?.querySelector('svg')?.getAttribute('data-icon')).toBe(icon.iconName);
        expect(renderedIcon?.style.color).toBe(color);
        expect(formatResult).toHaveBeenLastCalledWith(result, expect.objectContaining({ id: 1, type: ExerciseType.PROGRAMMING, course }), exercise.studentParticipations![0], true);
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
    it('keeps an active exercise-type filter visible when the supplied exercises change', () => {
        fixture.componentRef.setInput('exercises', [
            { id: 1, title: 'Text', type: ExerciseType.TEXT },
            { id: 2, title: 'Programming', type: ExerciseType.PROGRAMMING },
        ] as Exercise[]);
        fixture.detectChanges();
        fixture.componentInstance.type.set(ExerciseType.TEXT);
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([1]);
        fixture.componentRef.setInput('exercises', [{ id: 2, title: 'Programming', type: ExerciseType.PROGRAMMING }] as Exercise[]);
        fixture.detectChanges();
        expect(fixture.componentInstance.type()).toBe('all');
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([2]);
    });

    it('searches category names case-insensitively and combines the visible type and status controls', () => {
        fixture.componentRef.setInput('exercises', [
            { id: 1, title: 'Class design', type: ExerciseType.TEXT, categories: [{ category: 'Object orientation' }] },
            { id: 2, title: 'Loops', type: ExerciseType.PROGRAMMING },
        ] as Exercise[]);
        fixture.detectChanges();
        fixture.componentInstance.search.set('  ORIENTATION  ');
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([1]);
        fixture.componentInstance.type.set(ExerciseType.PROGRAMMING);
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows()).toHaveLength(0);
        fixture.componentInstance.search.set('');
        fixture.componentInstance.status.set('notSubmitted');
        fixture.detectChanges();
        expect(fixture.componentInstance.visibleRows().map((row) => row.exercise.id)).toEqual([2]);
    });

    it('explains that a course has no exercises and omits pagination for its empty ledger', () => {
        fixture.componentRef.setInput('course', { id: 589, exercises: [] });
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.redesign.noExercises');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.courseOverview.statistics.redesign.noMatchingExercises');
        expect(fixture.nativeElement.querySelector('tumaet-ui-paginator')).toBeNull();
    });

    it('distinguishes globally filtered exercises from a course with no exercises', () => {
        fixture.componentRef.setInput('course', { id: 589, exercises: [{ id: 1, title: 'Class design' }] });
        fixture.componentRef.setInput('exercises', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.redesign.noMatchingExercises');
        expect(fixture.nativeElement.querySelector('tumaet-ui-paginator')).toBeNull();
    });

    it('hides pagination for an empty search and restores linked results when the search is cleared', () => {
        fixture.componentRef.setInput('exercises', [{ id: 1, title: 'Class design' }] as Exercise[]);
        fixture.componentInstance.search.set('missing');
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.redesign.noMatchingExercises');
        expect(fixture.nativeElement.querySelector('tumaet-ui-paginator')).toBeNull();

        fixture.componentInstance.search.set('');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="statistics-exercise-link"]').getAttribute('href')).toBe('/courses/589/exercises/1');
        expect(fixture.nativeElement.querySelector('tumaet-ui-paginator')).not.toBeNull();
    });
});
