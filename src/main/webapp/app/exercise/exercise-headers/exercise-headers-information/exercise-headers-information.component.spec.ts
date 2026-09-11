import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { provideTranslateService } from '@ngx-translate/core';

import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { MockProvider } from 'ng-mocks';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { LockRepositoryPolicy, SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ExerciseHeadersInformationComponent', () => {
    let component: ExerciseHeadersInformationComponent;
    let fixture: ComponentFixture<ExerciseHeadersInformationComponent>;

    const baseExercise = {
        id: 42,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        course: {},
        dueDate: dayjs().subtract(1, 'weeks'),
        assessmentDueDate: dayjs().add(1, 'weeks'),
    } as unknown as Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseHeadersInformationComponent, NgbTooltipModule],
            providers: [
                MockProvider(ExerciseService),
                MockProvider(ComplaintService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideTranslateService(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { ...baseExercise });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('handing the status and due date to the title bar', () => {
        const STATUS = 'artemisApp.courseOverview.exerciseDetails.status';
        const SUBMISSION_DUE_OVER = 'artemisApp.courseOverview.exerciseDetails.submissionDueOver';
        const ASSESSMENT_DUE = 'artemisApp.courseOverview.exerciseDetails.assessmentDue';

        function titles(): string[] {
            return component.informationBoxItems().map((item) => item.title);
        }

        it('shows the two boxes in the panel while the title bar has no room for them', () => {
            expect(titles()).toContain(STATUS);
            expect(titles()).toContain(SUBMISSION_DUE_OVER);
        });

        it('drops exactly those two from the panel once the title bar shows them, so neither is stated twice', () => {
            const beforeHandover = titles();

            fixture.componentRef.setInput('titleBarShowsPills', true);
            fixture.detectChanges();

            const afterHandover = titles();
            expect(beforeHandover.filter((title) => !afterHandover.includes(title))).toEqual([SUBMISSION_DUE_OVER, STATUS]);
            // The panel keeps everything else, including the due dates that come after the submission deadline.
            expect(afterHandover).toContain(ASSESSMENT_DUE);
        });

        it('shows only those two in the title bar, laid out on one line', () => {
            fixture.componentRef.setInput('placement', 'titleBar');
            fixture.detectChanges();

            expect(titles()).toEqual([SUBMISSION_DUE_OVER, STATUS]);
            const box: HTMLElement = fixture.nativeElement.querySelector('#test-information-box');
            expect(box.classList).toContain('information-box-inline');
        });

        it('shows the status alone in the title bar when the exercise has no due date', () => {
            fixture.componentRef.setInput('exercise', { ...baseExercise, dueDate: undefined, assessmentDueDate: undefined });
            fixture.componentRef.setInput('placement', 'titleBar');
            fixture.detectChanges();

            expect(titles()).toEqual([STATUS]);
        });

        it('leaves the build progress bar to the panel, since it is two rows tall and a pill is one line', () => {
            const showsProgressBar = (): boolean => fixture.debugElement.query(By.directive(SubmissionResultStatusComponent)).componentInstance.showProgressBar();

            expect(showsProgressBar()).toBe(true);

            fixture.componentRef.setInput('placement', 'titleBar');
            fixture.detectChanges();

            expect(showsProgressBar()).toBe(false);
        });

        it('leaves the due date to the running quiz clock, which already holds that slot in the bar', () => {
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.QUIZ, dueDate: dayjs().add(1, 'hours') });
            fixture.componentRef.setInput('quizLiveHeaderInfo', { showRemainingTime: true, showResultsAvailable: false });
            fixture.componentRef.setInput('placement', 'titleBar');
            fixture.detectChanges();

            expect(titles()).toEqual([STATUS]);
        });
    });

    it('should render one information box per computed item', () => {
        const compiled = fixture.nativeElement as HTMLElement;
        const informationBoxes = compiled.querySelectorAll('jhi-information-box');
        expect(informationBoxes).toHaveLength(component.informationBoxItems().length);
        expect(component.informationBoxItems().length).toBeGreaterThan(0);
    });

    it('should display difficulty level component when the exercise has a difficulty', () => {
        fixture.componentRef.setInput('exercise', { ...baseExercise, difficulty: DifficultyLevel.EASY });
        fixture.detectChanges();

        expect(component.informationBoxItems().some((item) => item.content.type === 'difficultyLevel')).toBe(true);
        const difficultyLevelComponent = (fixture.nativeElement as HTMLElement).querySelector('jhi-difficulty-level');
        expect(difficultyLevelComponent).toBeTruthy();
    });

    it('should compute individualComplaintDueDate when course.maxComplaintTimeDays is defined', () => {
        const course = { id: 1, maxComplaintTimeDays: 7 } as Course;
        const result = { id: 1, completionDate: dayjs().subtract(2, 'day') } as Result;
        const studentParticipation = { id: 1, submissions: [{ results: [result] }] } as StudentParticipation;
        const expectedDueDate = dayjs().add(7, 'days');
        vi.spyOn(ComplaintService, 'getIndividualComplaintDueDate').mockReturnValue(expectedDueDate);

        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('studentParticipation', studentParticipation);
        fixture.detectChanges();

        expect(component.individualComplaintDueDate()).toBe(expectedDueDate);
    });

    it('should not compute individualComplaintDueDate when course has no maxComplaintTimeDays', () => {
        const spy = vi.spyOn(ComplaintService, 'getIndividualComplaintDueDate');
        fixture.componentRef.setInput('course', { id: 1 } as Course);
        fixture.detectChanges();

        expect(component.individualComplaintDueDate()).toBeUndefined();
        expect(spy).not.toHaveBeenCalled();
    });

    it('should build a points item with the achieved and max points', () => {
        const pointsItem = component.getPointsItem('points', 10, 5);
        expect(pointsItem.title).toBe('artemisApp.courseOverview.exerciseDetails.points');
        expect(pointsItem.content).toEqual({ type: 'string', value: '5 / 10' });
    });

    it('should add points and bonus items when the exercise has maxPoints and bonusPoints', () => {
        fixture.componentRef.setInput('exercise', { ...baseExercise, maxPoints: 10, bonusPoints: 5 });
        fixture.detectChanges();

        const pointsTitles = component.informationBoxItems().map((item) => item.title);
        expect(pointsTitles).toContain('artemisApp.courseOverview.exerciseDetails.points');
        expect(pointsTitles).toContain('artemisApp.courseOverview.exerciseDetails.bonus');
    });

    it('should add a start date item when the start date is in the future', () => {
        fixture.componentRef.setInput('exercise', { ...baseExercise, startDate: dayjs().add(2, 'weeks') });
        fixture.detectChanges();

        expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.startDate')).toBe(true);
    });

    it('should add an assessment due date item when the due date is past and the assessment due date is in the future', () => {
        expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.assessmentDue')).toBe(true);
    });

    it('should return correct submission color based on submissions left', () => {
        // limit 3
        expect(component.getSubmissionColor(1, 3)).toBe('body-color');
        expect(component.getSubmissionColor(2, 3)).toBe('warning');
        expect(component.getSubmissionColor(3, 3)).toBe('danger');
        expect(component.getSubmissionColor(4, 3)).toBe('danger');
        // unlimited (no limit)
        expect(component.getSubmissionColor(0, undefined)).toBe('body-color');
        expect(component.getSubmissionColor(1, undefined)).toBe('body-color');
        expect(component.getSubmissionColor(2, undefined)).toBe('body-color');
    });

    it('should include a submission policy item when the policy is active and has a limit', () => {
        const submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.active = true;
        submissionPolicy.submissionLimit = 5;
        fixture.componentRef.setInput('submissionPolicy', submissionPolicy);
        fixture.detectChanges();

        const policyItem = component.informationBoxItems().find((item) => item.title === 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle');
        expect(policyItem).toBeTruthy();
        expect((policyItem!.content as { value: string }).value).toBe('0 /  5');
    });

    it('should not include a submission policy item when the policy is inactive', () => {
        const submissionPolicy = { active: false, submissionLimit: 5 } as SubmissionPolicy;
        fixture.componentRef.setInput('submissionPolicy', submissionPolicy);
        fixture.detectChanges();

        expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle')).toBe(false);
    });

    it('should count unique manual submissions', () => {
        const mockSubmissions: ProgrammingSubmission[] = [
            { type: SubmissionType.MANUAL, commitHash: 'hash1' },
            { type: SubmissionType.MANUAL, commitHash: 'hash2' },
            { type: SubmissionType.MANUAL, commitHash: 'hash1' }, // Duplicate commit hash
            { type: SubmissionType.INSTRUCTOR, commitHash: 'hash3' }, // Different submission type
        ];
        fixture.componentRef.setInput('studentParticipation', { submissions: mockSubmissions } as StudentParticipation);
        fixture.detectChanges();

        expect(component.numberOfSubmissions()).toBe(2);
    });

    it('should default sortedHistoryResults to an empty array when there is no participation', () => {
        expect(component.sortedHistoryResults()).toEqual([]);
    });

    it('should derive sortedHistoryResults from the participation, sorted by id descending', () => {
        const studentParticipation = {
            submissions: [{ results: [{ id: 1, score: 80 } as Result] }, { results: [{ id: 2, score: 90 } as Result] }],
        } as StudentParticipation;
        fixture.componentRef.setInput('studentParticipation', studentParticipation);
        fixture.detectChanges();

        expect(component.sortedHistoryResults().map((result) => result.id)).toEqual([2, 1]);
    });

    it('should count an unrated result towards achievedPoints only in practice mode', () => {
        fixture.componentRef.setInput('exercise', { ...baseExercise, maxPoints: 10 });
        fixture.componentRef.setInput('studentParticipation', { submissions: [{ results: [{ id: 1, score: 80, rated: false } as Result] }] } as StudentParticipation);

        // Graded mode ignores the unrated result.
        fixture.componentRef.setInput('isPractice', false);
        fixture.detectChanges();
        expect(component.achievedPoints()).toBe(0);

        // Practice results are unrated, so practice mode still uses the latest result.
        fixture.componentRef.setInput('isPractice', true);
        fixture.detectChanges();
        expect(component.achievedPoints()).toBe(8);
    });

    it('should not make the status clickable when there are no history results', () => {
        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.querySelector('[role="button"]')).toBeNull();
    });

    it('should make the status clickable when there are history results', () => {
        const studentParticipation = {
            submissions: [{ results: [{ id: 1, score: 50 } as Result] }],
        } as StudentParticipation;
        fixture.componentRef.setInput('studentParticipation', studentParticipation);
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.querySelector('[role="button"]')).toBeTruthy();
    });

    it('should leave the remaining time to the title bar and keep results-available last', () => {
        fixture.componentRef.setInput('quizLiveHeaderInfo', {
            showRemainingTime: true,
            remainingTimeText: '5 min',
            remainingTimeColor: 'warning',
            showResultsAvailable: true,
            resultsAvailableDate: dayjs(),
        });
        fixture.detectChanges();

        const items = component.informationBoxItems();
        const titles = items.map((item) => item.title);

        // The remaining time belongs to the title bar's countdown; repeating it here would say the same thing twice.
        expect(titles).not.toContain('artemisApp.quizExercise.remainingTime');

        // Results available stays last.
        const lastItem = items[items.length - 1];
        expect(lastItem.title).toBe('artemisApp.quizExercise.resultsAvailable');
        expect(lastItem.content.type).toBe('dateTime');
    });

    it('should not add quiz live-info boxes when quizLiveHeaderInfo is undefined', () => {
        const titles = component.informationBoxItems().map((item) => item.title);
        expect(titles).not.toContain('artemisApp.quizExercise.remainingTime');
        expect(titles).not.toContain('artemisApp.quizExercise.resultsAvailable');
    });

    it('should suppress the generic submission-due box only while the live quiz countdown is shown', () => {
        // Without quiz info, the (past) due date renders the submission-due-over box.
        expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.submissionDueOver')).toBe(true);

        // Countdown active in the title bar: no due-date box here, and no duplicate of the clock either.
        fixture.componentRef.setInput('quizLiveHeaderInfo', { showRemainingTime: true, remainingTimeText: '5 min', showResultsAvailable: false });
        fixture.detectChanges();

        let titles = component.informationBoxItems().map((item) => item.title);
        expect(titles).not.toContain('artemisApp.courseOverview.exerciseDetails.submissionDueOver');
        expect(titles).not.toContain('artemisApp.courseOverview.exerciseDetails.submissionDue');
        // Nor is the time repeated here: the title bar's countdown has that slot for as long as it runs.
        expect(titles).not.toContain('artemisApp.quizExercise.remainingTime');

        // Quiz info present but countdown not shown (e.g. before start / results / preview): the due date is shown again.
        fixture.componentRef.setInput('quizLiveHeaderInfo', { showRemainingTime: false, showResultsAvailable: false });
        fixture.detectChanges();

        titles = component.informationBoxItems().map((item) => item.title);
        expect(titles).toContain('artemisApp.courseOverview.exerciseDetails.submissionDueOver');
    });

    describe('AI feedback quota box', () => {
        const athenaCourse = { athenaFormativeFeedbackEnabled: true } as Course;

        it('should show the AI feedback item for a text exercise when Athena and the course flag are enabled', () => {
            fixture.componentRef.setInput('athenaEnabled', true);
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.TEXT });
            fixture.componentRef.setInput('course', athenaCourse);
            fixture.detectChanges();

            expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests')).toBe(true);
        });

        it('should show the AI feedback item for a programming exercise with semi-automatic assessment', () => {
            fixture.componentRef.setInput('athenaEnabled', true);
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.SEMI_AUTOMATIC });
            fixture.componentRef.setInput('course', athenaCourse);
            fixture.detectChanges();

            expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests')).toBe(true);
        });

        it('should not show the AI feedback item for a programming exercise without semi-automatic assessment', () => {
            fixture.componentRef.setInput('athenaEnabled', true);
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC });
            fixture.componentRef.setInput('course', athenaCourse);
            fixture.detectChanges();

            expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests')).toBe(false);
        });

        it('should not show the AI feedback item for a file-upload exercise even when Athena is enabled course-wide', () => {
            fixture.componentRef.setInput('athenaEnabled', true);
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.FILE_UPLOAD });
            fixture.componentRef.setInput('course', athenaCourse);
            fixture.detectChanges();

            expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests')).toBe(false);
        });

        it('should not show the AI feedback item for a quiz exercise even when Athena is enabled course-wide', () => {
            fixture.componentRef.setInput('athenaEnabled', true);
            fixture.componentRef.setInput('exercise', { ...baseExercise, type: ExerciseType.QUIZ });
            fixture.componentRef.setInput('course', athenaCourse);
            fixture.detectChanges();

            expect(component.informationBoxItems().some((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests')).toBe(false);
        });
    });
});
