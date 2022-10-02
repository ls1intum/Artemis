import { TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Exam } from 'app/entities/exam.model';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import dayjs from 'dayjs/esm';
import { Result } from 'app/entities/result.model';

describe('HeaderParticipationPage', () => {
    let component: HeaderParticipationPageComponent;

    let exercise: ProgrammingExercise;
    let participation: StudentParticipation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                HeaderParticipationPageComponent,
                MockComponent(DifficultyBadgeComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                const fixture = TestBed.createComponent(HeaderParticipationPageComponent);
                component = fixture.componentInstance;

                exercise = new ProgrammingExercise(undefined, undefined);
                component.exercise = exercise;

                participation = new StudentParticipation(ParticipationType.PROGRAMMING);
            });
    });

    it('should set the status badge', () => {
        const dueDate1 = dayjs().subtract(2, 'days');
        exercise.dueDate = dueDate1;
        component.ngOnInit();
        expect(component.exerciseStatusBadge).toBe('bg-danger');
        expect(component.dueDate).toEqual(dueDate1);

        const dueDate2 = dayjs().add(1, 'day');
        participation.individualDueDate = dueDate2;
        component.participation = participation;
        component.ngOnChanges();
        expect(component.exerciseStatusBadge).toBe('bg-success');
        expect(component.dueDate).toEqual(dueDate2);
    });

    it('should always publish the results for regular exercises', () => {
        expect(component.resultsPublished).toBeTrue();

        exercise.exerciseGroup = new ExerciseGroup();
        expect(component.resultsPublished).toBeTrue();
    });

    it('should only publish the results for exam exercises after the publishing date', () => {
        const exam = new Exam();
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = exam;
        exerciseGroup.exercises = [exercise];
        exercise.exerciseGroup = exerciseGroup;

        // no publishing date => do not publish
        expect(component.resultsPublished).toBeFalse();

        exam.publishResultsDate = dayjs().subtract(1, 'day');
        expect(component.resultsPublished).toBeTrue();

        exam.publishResultsDate = dayjs().add(1, 'day');
        expect(component.resultsPublished).toBeFalse();
    });

    it('should not apply changes if no exercise is set', () => {
        // @ts-ignore
        component.exercise = undefined;
        component.ngOnChanges();

        // Expect default values
        expect(component.exerciseStatusBadge).toBe('bg-success');
        expect(component.exerciseCategories).toBeUndefined();
        expect(component.dueDate).toBeUndefined();
    });

    it('should display achieved points accordingly', () => {
        component.exercise.maxPoints = 100;
        component.participation = { results: [] } as StudentParticipation;
        component.ngOnChanges();
        expect(component.achievedPoints).toBeUndefined();

        component.participation = { results: [{ score: 42 } as Result] } as StudentParticipation;
        component.ngOnChanges();
        expect(component.achievedPoints).toBeUndefined();

        component.participation = { results: [{ score: 42, rated: true } as Result] } as StudentParticipation;
        component.ngOnChanges();
        expect(component.achievedPoints).toBe(42);
    });
});
