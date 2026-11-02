import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { NotReleasedTagComponent } from 'app/shared-ui/components/not-released-tag/not-released-tag.component';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ExerciseTypePipe } from 'app/foundation/pipes/exercise-type.pipe';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Submission, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { LockRepositoryPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('HeaderExercisePageWithDetails', () => {
    let component: HeaderExercisePageWithDetailsComponent;
    let fixture: ComponentFixture<HeaderExercisePageWithDetailsComponent>;

    let exam: Exam;
    let exercise: ProgrammingExercise;
    let participation: StudentParticipation;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                HeaderExercisePageWithDetailsComponent,
                MockComponent(DifficultyBadgeComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(NotReleasedTagComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ExerciseTypePipe),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(HeaderExercisePageWithDetailsComponent);
        component = fixture.componentInstance;

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = undefined;
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        fixture.componentRef.setInput('exercise', exercise);

        exam = new Exam();
        participation = new StudentParticipation(ParticipationType.PROGRAMMING);
    });

    it('should initialise badges, icons, and categories', () => {
        component.ngOnInit();

        expect(component.exerciseCategories()).toEqual([]);
        expect(component.nextRelevantDate()).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge()).toBeUndefined();
        // @ts-ignore
        expect(component.icon.iconName).toBe('keyboard');

        // dueDate, categories, examMode should also be set if the necessary information is known
        const category = new ExerciseCategory('testcategory', undefined);
        const categories = [category];
        exercise.categories = categories;
        exam.endDate = dayjs().subtract(1, 'day');
        fixture.componentRef.setInput('exam', exam);

        component.ngOnInit();

        expect(component.exerciseCategories()).toEqual(categories);
        expect(component.nextRelevantDate()).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-danger');
    });

    it('should set the icon according to the exercise due date', () => {
        exercise.assessmentType = AssessmentType.MANUAL;

        exercise.releaseDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exercise.releaseDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');

        exercise.releaseDate = undefined;
        exercise.startDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exercise.startDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');

        exercise.startDate = undefined;
        exercise.dueDate = dayjs().subtract(2, 'days');
        participation.individualDueDate = dayjs().add(1, 'day');
        fixture.componentRef.setInput('studentParticipation', participation);
        component.ngOnInit();
        expect(component.dueDate()).toEqual(participation.individualDueDate);
        expect(component.nextRelevantDate()).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge()).toBeUndefined();

        participation.individualDueDate = undefined;
        exercise.assessmentDueDate = dayjs().add(2, 'days');
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exercise.assessmentDueDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');

        exercise.assessmentDueDate = dayjs().subtract(1, 'days');
        fixture.componentRef.setInput('course', { maxComplaintTimeDays: 7 } as Course);
        const submission = { results: [{ rated: true, completionDate: dayjs() }] } as ProgrammingSubmission;
        participation.submissions = [submission];
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(participation.submissions![0].results![0].completionDate?.add(7, 'days'));
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');

        participation.submissionCount = 1;
        const submission2 = { results: [{ rated: false }] } as ProgrammingSubmission;
        participation.submissions = [submission2];
        exercise.assessmentType = AssessmentType.MANUAL;
        exercise.dueDate = dayjs().subtract(3, 'months');
        component.ngOnInit();
        expect(component.nextRelevantDate()).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge()).toBeUndefined();
        expect(component.canComplainLaterOn()).toBe(true);

        exercise.assessmentDueDate = dayjs().subtract(2, 'months');
        const submission3 = { results: [{ rated: true, completionDate: dayjs().subtract(1, 'month') }] } as ProgrammingSubmission;
        participation.submissions = [submission3];
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(participation.submissions![0].results![0].completionDate?.add(7, 'days'));
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-danger');
        expect(component.canComplainLaterOn()).toBe(false);
    });

    it('should not show an earlier date than the dueDate once over', () => {
        exercise.dueDate = dayjs().add(2, 'days');
        exercise.releaseDate = dayjs().subtract(1, 'day');
        component.ngOnInit();
        expect(component.nextRelevantDate()).toBeUndefined();
        expect(component.nextRelevantDateLabel()).toBeUndefined();
        expect(component.nextRelevantDateStatusBadge()).toBeUndefined();
    });

    it('should determine correct badge for due date', () => {
        exercise.dueDate = dayjs().add(2, 'days');
        component.ngOnInit();
        expect(component.dueDateStatusBadge()).toBe('bg-state-success');

        exercise.dueDate = dayjs().subtract(2, 'days');
        component.ngOnInit();
        expect(component.dueDateStatusBadge()).toBe('bg-state-danger');
    });

    it('should set the icon according to the exam end date', () => {
        exam.endDate = dayjs().subtract(1, 'day');
        fixture.componentRef.setInput('exam', exam);
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-danger');

        exam.publishResultsDate = dayjs().add(12, 'hours');
        fixture.componentRef.setInput('exam', exam);
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exam.publishResultsDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');

        exam.publishResultsDate = dayjs().subtract(12, 'hours');
        exam.endDate = dayjs().add(1, 'day');
        fixture.componentRef.setInput('exam', exam);
        component.ngOnInit();
        expect(component.nextRelevantDate()).toEqual(exam.endDate);
        expect(component.nextRelevantDateStatusBadge()).toBe('bg-state-success');
    });

    it('should not set a due date in exam mode as no individual due dates exist', () => {
        exercise.dueDate = dayjs();
        exam.endDate = dayjs().add(1, 'day');
        fixture.componentRef.setInput('exam', exam);

        component.ngOnInit();

        expect(component.dueDate()).toBeUndefined();
    });

    it.each([
        [[] as Submission[], 0],
        [[{ type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission] as Submission[], 1],
        [[{ type: SubmissionType.INSTRUCTOR, commitHash: 'first' } as ProgrammingSubmission] as Submission[], 0],
        [
            [
                { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission,
                { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission,
            ] as Submission[],
            1,
        ],
        [
            [
                { type: SubmissionType.MANUAL, commitHash: 'first' } as ProgrammingSubmission,
                { type: SubmissionType.MANUAL, commitHash: 'second' } as ProgrammingSubmission,
            ] as Submission[],
            2,
        ],
    ])('should count number of submissions correctly', (submissions: Submission[], expectedNumber: number) => {
        participation.submissions = submissions;
        fixture.componentRef.setInput('studentParticipation', participation);
        const submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.active = true;
        fixture.componentRef.setInput('submissionPolicy', submissionPolicy);

        expect(component.numberOfSubmissions()).toBe(expectedNumber);
    });
    describe.each(['releaseDate', 'startDate', 'assessmentDue', 'complaintDue'])('presentation summary at %s', (dateLabel) => {
        it.each([
            { coursePresentationScore: 1, participationScore: 1, expectedBadge: 'global.generic.yes', expectedScore: undefined },
            { coursePresentationScore: 1, participationScore: 0, expectedBadge: 'global.generic.no', expectedScore: undefined },
            { coursePresentationScore: undefined, participationScore: 50, expectedBadge: undefined, expectedScore: '50%' },
            { coursePresentationScore: undefined, participationScore: 0, expectedBadge: 'global.generic.unset', expectedScore: undefined },
        ])(
            'preserves the summary for course setting $coursePresentationScore and student score $participationScore',
            ({ coursePresentationScore, participationScore, expectedBadge, expectedScore }) => {
                exercise.presentationScoreEnabled = true;
                const course = new Course();
                course.presentationScore = coursePresentationScore;
                participation.presentationScore = participationScore;
                fixture.componentRef.setInput('course', course);
                fixture.componentRef.setInput('studentParticipation', participation);
                fixture.detectChanges();
                component.nextRelevantDateLabel.set(dateLabel);
                component.nextRelevantDate.set(undefined);
                component.canComplainLaterOn.set(false);
                fixture.detectChanges();

                const summary: HTMLElement = fixture.nativeElement.querySelector('.right-col');
                expect(summary.querySelectorAll('[data-testid="presentation-score-summary"]')).toHaveLength(1);
                if (expectedBadge) {
                    expect(summary.querySelector(`[jhiTranslate="${expectedBadge}"]`)).not.toBeNull();
                } else {
                    expect(summary.textContent).toContain(expectedScore);
                }
            },
        );

        it('omits the summary when presentation scoring is disabled', () => {
            exercise.presentationScoreEnabled = false;
            fixture.detectChanges();
            component.nextRelevantDateLabel.set(dateLabel);
            component.nextRelevantDate.set(undefined);
            component.canComplainLaterOn.set(false);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.right-col').querySelectorAll('.badge')).toHaveLength(0);
        });
    });
});
