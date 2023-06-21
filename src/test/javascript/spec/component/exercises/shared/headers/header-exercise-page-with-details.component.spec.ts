import { TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ExerciseTypePipe } from 'app/shared/pipes/exercise-type.pipe';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';
import { LockRepositoryPolicy } from 'app/entities/submission-policy.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

describe('HeaderExercisePageWithDetails', () => {
    let component: HeaderExercisePageWithDetailsComponent;

    let exam: Exam;
    let exercise: ProgrammingExercise;
    let participation: StudentParticipation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                HeaderExercisePageWithDetailsComponent,
                MockComponent(DifficultyBadgeComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(NotReleasedTagComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ExerciseTypePipe),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                const fixture = TestBed.createComponent(HeaderExercisePageWithDetailsComponent);
                component = fixture.componentInstance;

                exercise = new ProgrammingExercise(undefined, undefined);
                exercise.dueDate = undefined;
                exercise.assessmentType = AssessmentType.AUTOMATIC;
                component.exercise = exercise;

                exam = new Exam();
                participation = new StudentParticipation(ParticipationType.PROGRAMMING);
            });
    });

    it('should initialise badges, icons, and categories', () => {
        component.ngOnInit();

        expect(component.exerciseCategories).toEqual([]);
        expect(component.nextRelevantDate).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge).toBeUndefined();
        // @ts-ignore
        expect(component.icon.iconName).toBe('keyboard');

        // dueDate, categories, examMode should also be set if the necessary information is known
        const category = new ExerciseCategory();
        category.category = 'testcategory';
        const categories = [category];
        exercise.categories = categories;
        exam.endDate = dayjs().subtract(1, 'day');
        component.exam = exam;

        component.ngOnInit();

        expect(component.exerciseCategories).toEqual(categories);
        expect(component.nextRelevantDate).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-danger');
    });

    it('should set the icon according to the exercise due date', () => {
        exercise.assessmentType = AssessmentType.MANUAL;

        exercise.releaseDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exercise.releaseDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');

        exercise.releaseDate = undefined;
        exercise.startDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exercise.startDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');

        exercise.startDate = undefined;
        exercise.dueDate = dayjs().subtract(2, 'days');
        participation.individualDueDate = dayjs().add(1, 'day');
        component.studentParticipation = participation;
        component.ngOnInit();
        expect(component.dueDate).toEqual(participation.individualDueDate);
        expect(component.nextRelevantDate).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge).toBeUndefined();

        participation.individualDueDate = undefined;
        exercise.assessmentDueDate = dayjs().add(2, 'days');
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exercise.assessmentDueDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');

        exercise.assessmentDueDate = dayjs().subtract(1, 'days');
        component.course = { maxComplaintTimeDays: 7 } as Course;
        participation.results = [{ rated: true, completionDate: dayjs() } as Result];
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(participation.results[0].completionDate?.add(7, 'days'));
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');

        participation.submissionCount = 1;
        participation.results = [{ rated: false } as Result];
        exercise.assessmentType = AssessmentType.MANUAL;
        exercise.dueDate = dayjs().subtract(3, 'months');
        component.ngOnInit();
        expect(component.nextRelevantDate).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge).toBeUndefined();
        expect(component.canComplainLaterOn).toBeTrue();

        exercise.assessmentDueDate = dayjs().subtract(2, 'months');
        participation.results = [{ rated: true, completionDate: dayjs().subtract(1, 'month') } as Result];
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(participation.results[0].completionDate?.add(7, 'days'));
        expect(component.nextRelevantDateStatusBadge).toBe('bg-danger');
        expect(component.canComplainLaterOn).toBeFalse();
    });

    it('should not show an earlier date than the dueDate once over', () => {
        exercise.dueDate = dayjs().add(2, 'days');
        exercise.releaseDate = dayjs().subtract(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate).toBeUndefined();
        expect(component.nextRelevantDateLabel).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge).toBeUndefined();
    });

    it('should determine correct badge for due date', () => {
        exercise.dueDate = dayjs().add(2, 'days');
        component.ngOnInit();
        expect(component.dueDateStatusBadge).toBe('bg-success');

        exercise.dueDate = dayjs().subtract(2, 'days');
        component.ngOnInit();
        expect(component.dueDateStatusBadge).toBe('bg-danger');
    });

    it('should set the icon according to the exam end date', () => {
        exam.endDate = dayjs().subtract(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-danger');

        exam.publishResultsDate = dayjs().add(12, 'hours');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exam.publishResultsDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');

        exam.publishResultsDate = dayjs().subtract(12, 'hours');
        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextRelevantDate).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge).toBe('bg-success');
    });

    it('should not set a due date in exam mode as no individual due dates exist', () => {
        exercise.dueDate = dayjs();
        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;

        component.ngOnInit();

        expect(component.dueDate).toBeUndefined();
    });

    it.each([
        [[] as Result[], 0],
        [[{ submission: { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission }] as Result[], 1],
        [[{ submission: { type: SubmissionType.INSTRUCTOR, commitHash: 'first' } as ProgrammingSubmission }] as Result[], 0],
        [
            [
                { submission: { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission },
                { submission: { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission },
            ] as Result[],
            1,
        ],
        [
            [
                { submission: { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission },
                { submission: { type: SubmissionType.MANUAL, commitHash: 'second' } as ProgrammingSubmission },
            ] as Result[],
            2,
        ],
    ])('should count number of submissions correctly', (results: Result[], expectedNumber: number) => {
        participation.results = results;
        component.studentParticipation = participation;
        component.submissionPolicy = new LockRepositoryPolicy();
        component.submissionPolicy.active = true;

        component.ngOnChanges();

        expect(component.numberOfSubmissions).toBe(expectedNumber);
    });
});
