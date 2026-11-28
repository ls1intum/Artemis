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
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { LockRepositoryPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission-type.model';

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
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisServerDateService, useValue: { now: () => dayjs() } },
            ],
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

    it('shows a test-run label only in test-run mode', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-header-test-run"]')).toBeNull();
        fixture.componentRef.setInput('isTestRun', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-header-test-run"]')).not.toBeNull();
    });

    it.each([
        { hours: 12, type: 'timeAgo', color: 'danger' },
        { hours: 48, type: 'timeAgo', color: 'body-color' },
        { hours: 240, type: 'dateTime', color: 'body-color' },
        { hours: -24, type: 'dateTime', color: undefined },
    ])('formats an assessment deadline $hours hours away like a submission deadline', ({ hours, type, color }) => {
        const date = dayjs().add(hours, 'hours');
        component.nextRelevantDateLabel.set('assessmentDue');
        component.nextRelevantDate.set(date);
        const information = component.nextDateInformation();
        expect(information.title).toBe('artemisApp.courseOverview.exerciseDetails.assessmentDue');
        expect(information.content).toEqual({ type, value: date });
        expect(information.contentColor).toBe(color);
        expect(component.nextRelevantDate()).toBe(date);
        expect(component.nextRelevantDateLabel()).toBe('assessmentDue');
    });

    it.each(['endDate', 'publishResultsDate'])('uses existing exam translations for %s', (label) => {
        fixture.componentRef.setInput('exam', new Exam());
        component.nextRelevantDateLabel.set(label);
        expect(component.nextDateInformation().title).toBe(`artemisApp.exam.${label}`);
        fixture.componentRef.setInput('exam', undefined);
        component.nextRelevantDateLabel.set('assessmentDue');
        expect(component.nextDateInformation().title).toBe('artemisApp.courseOverview.exerciseDetails.assessmentDue');
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
        expect(component.dueDateInformation()?.title).toBe('artemisApp.courseOverview.exerciseDetails.submissionDue');

        exercise.dueDate = dayjs().subtract(2, 'days');
        component.ngOnInit();
        expect(component.dueDateInformation()?.title).toBe('artemisApp.courseOverview.exerciseDetails.submissionDueOver');
        expect(component.dueDateInformation()?.content.type).toBe('dateTime');
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

    it.each([-1, 1])('shows the exam end date when it is %s days from now', (days) => {
        exam.endDate = dayjs().add(days, 'day');
        exercise.dueDate = dayjs().add(3, 'days');
        fixture.componentRef.setInput('exam', exam);
        fixture.detectChanges();

        expect(component.dueDateInformation()).toEqual({
            title: 'artemisApp.exam.endDate',
            content: { type: 'dateTime', value: exam.endDate },
            isContentComponent: true,
        });
    });

    it('omits the exam deadline for test runs and exams without an end date', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.detectChanges();
        expect(component.dueDateInformation()).toBeUndefined();

        const scheduledExam = new Exam();
        scheduledExam.endDate = dayjs().add(1, 'day');
        fixture.componentRef.setInput('exam', scheduledExam);
        fixture.componentRef.setInput('isTestRun', true);
        fixture.detectChanges();
        expect(component.dueDateInformation()).toBeUndefined();
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
});
