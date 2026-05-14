import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { MockProvider } from 'ng-mocks';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { LockRepositoryPolicy, SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionType, getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { DateContent, InformationBox, StringNumberContent } from 'app/shared/information-box/information-box.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('ExerciseHeadersInformationComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseHeadersInformationComponent;
    let fixture: ComponentFixture<ExerciseHeadersInformationComponent>;
    let exerciseService: ExerciseService;
    let getExerciseDetailsMock: ReturnType<typeof vi.spyOn>;

    const exercise = {
        id: 42,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        course: {},
        dueDate: dayjs().subtract(1, 'weeks'),
        assessmentDueDate: dayjs().add(1, 'weeks'),
    } as unknown as Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseHeadersInformationComponent, TranslateModule.forRoot(), NgbTooltipModule],
            providers: [
                MockProvider(ExerciseService),
                MockProvider(ComplaintService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
                component = fixture.componentInstance;
                exerciseService = TestBed.inject(ExerciseService);
                getExerciseDetailsMock = vi.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));
                component.exercise = { ...exercise };
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display the information box items', () => {
        component.informationBoxItems = [
            {
                title: 'Test Title 1',
                tooltip: 'Test Tooltip 1',
                tooltipParams: {},
                isContentComponent: false,
                content: { type: 'string', value: 'Test Content' },
                contentColor: 'primary',
            },
            {
                title: 'Test Title 2',
                tooltip: 'Test Tooltip 2',
                tooltipParams: {},
                isContentComponent: false,
                content: { type: 'string', value: 'Test Content' },
            },
        ];
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const informationBoxes = compiled.querySelectorAll('jhi-information-box');
        expect(informationBoxes).toHaveLength(2);
    });

    it('should display difficulty level component when content type is difficultyLevel', () => {
        component.informationBoxItems = [
            {
                title: 'Difficulty Level',
                tooltip: 'Difficulty Tooltip',
                tooltipParams: {},
                isContentComponent: true,
                content: { type: 'difficultyLevel', value: DifficultyLevel.EASY },
            },
        ];
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const difficultyLevelComponent = compiled.querySelector('jhi-difficulty-level');
        expect(difficultyLevelComponent).toBeTruthy();
    });

    it('should set individualComplaintDueDate if course.maxComplaintTimeDays is defined', () => {
        const course: Course = { id: 1, maxComplaintTimeDays: 7 } as Course;
        const result: Result = { id: 1, completionDate: dayjs().subtract(2, 'day') } as Result;
        const studentParticipation: StudentParticipation = {
            id: 1,
            submissions: [{ results: [result] }],
        } as StudentParticipation;

        component.course = course;
        component.studentParticipation = studentParticipation;

        const expectedDueDate = dayjs().add(7, 'days');
        vi.spyOn(ComplaintService, 'getIndividualComplaintDueDate').mockReturnValue(expectedDueDate);

        if (component.course?.maxComplaintTimeDays) {
            component.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                component.exercise,
                component.course.maxComplaintTimeDays,
                getAllResultsOfAllSubmissions(component.studentParticipation.submissions).last(),
                component.studentParticipation,
            );
        }
    });

    it('should add points item to informationBoxItems', () => {
        const maxPoints = 10;
        const achievedPoints = 5;
        const pointsContent: StringNumberContent = { type: 'string', value: maxPoints };
        const pointsItem: InformationBox = { title: 'Points', content: pointsContent };

        vi.spyOn(component, 'getPointsItem').mockReturnValue(pointsItem);

        component.informationBoxItems = [];
        component.informationBoxItems.push(component.getPointsItem('points', maxPoints, achievedPoints));

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(pointsItem);
    });

    it('should add bonus points item to informationBoxItems', () => {
        const bonusPoints = 5;
        const achievedBonusPoints = 2;
        const pointsContent: StringNumberContent = { type: 'string', value: bonusPoints };
        const pointsItem: InformationBox = { title: 'Bonus Points', content: pointsContent };

        vi.spyOn(component, 'getPointsItem').mockReturnValue(pointsItem);

        component.informationBoxItems = [];
        component.informationBoxItems.push(component.getPointsItem('bonus', bonusPoints, achievedBonusPoints));

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(pointsItem);
    });

    it('should add start date item to informationBoxItems if startDate is in the future', () => {
        const exerciseWithStartDate = {
            id: 43,
            type: ExerciseType.TEXT,
            studentParticipations: [],
            course: {},
            dueDate: dayjs().add(1, 'weeks'),
            assessmentDueDate: dayjs().add(1, 'weeks'),
            startDate: dayjs().add(3, 'days'),
        } as unknown as Exercise;

        component.exercise = { ...exerciseWithStartDate };
        const startDateContent: DateContent = {
            type: 'dateTime',
            value: dayjs().add(3, 'days'),
        };
        const startDateItem: InformationBox = {
            title: 'artemisApp.courseOverview.exerciseDetails.startDate',
            content: startDateContent,
            isContentComponent: true,
        };
        component.informationBoxItems = [];

        fixture.changeDetectorRef.detectChanges();

        if (component.exercise.startDate && dayjs().isBefore(component.exercise.startDate)) {
            component.informationBoxItems.push(startDateItem);
        }

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(startDateItem);
    });

    it('should return correct submission color based on submissions left', () => {
        const submissionPolicyWithLimit = { submissionLimit: 3 } as SubmissionPolicy;
        const submissionPolicyWithoutLimit = { submissionLimit: undefined } as SubmissionPolicy;

        // Case 1: More than 1 submission left
        component.submissionPolicy = submissionPolicyWithLimit;
        component.numberOfSubmissions = 1;
        expect(component.getSubmissionColor()).toBe('body-color');

        // Case 2: Exactly 1 submission left
        component.numberOfSubmissions = 2;
        expect(component.getSubmissionColor()).toBe('warning');

        // Case 3: No submissions left
        component.numberOfSubmissions = 3;
        expect(component.getSubmissionColor()).toBe('danger');

        // Case 4: One more than the limit
        component.numberOfSubmissions = 4;
        expect(component.getSubmissionColor()).toBe('danger');

        // Case 5: Unlimited submissions left
        component.submissionPolicy = submissionPolicyWithoutLimit;
        component.numberOfSubmissions = 0;
        expect(component.getSubmissionColor()).toBe('body-color');

        // Case 6: Unlimited submissions with 1 submission done
        component.numberOfSubmissions = 1;
        expect(component.getSubmissionColor()).toBe('body-color');

        // Case 7: Unlimited submissions with 2 submissions done
        component.numberOfSubmissions = 2;
        expect(component.getSubmissionColor()).toBe('body-color');
    });

    it('should add assessment due date item to informationBoxItems if dueDate is in the past and assessmentDueDate is in the future', () => {
        const now = dayjs();
        const dueDate = now.subtract(1, 'day');

        const assessmentDueContent: DateContent = { type: 'dateTime', value: dayjs().add(1, 'weeks') };
        const assessmentDueItem: InformationBox = {
            title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
            content: assessmentDueContent,
            isContentComponent: true,
            tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
        };
        component.dueDate = dueDate;
        component.informationBoxItems = [];

        if (component.dueDate?.isBefore(now) && component.exercise.assessmentDueDate?.isAfter(now)) {
            component.informationBoxItems.push(assessmentDueItem);
        }

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(assessmentDueItem);
    });

    it('should update submission policy item in informationBoxItems', () => {
        vi.spyOn(component, 'countSubmissions').mockImplementation(() => {});

        const mockSubmissionPolicyItem: InformationBox = {
            title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
            content: { type: 'string', value: 'Updated Item' } as StringNumberContent,
        };
        vi.spyOn(component, 'getSubmissionPolicyItem').mockReturnValue(mockSubmissionPolicyItem);

        component.informationBoxItems = [
            {
                title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
                content: { type: 'string', value: 'Original Item' } as StringNumberContent,
            },
        ];

        component.updateSubmissionPolicyItem();

        expect(component.countSubmissions).toHaveBeenCalled();
        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(mockSubmissionPolicyItem);
    });

    it('should correctly count unique manual submissions', () => {
        const mockSubmissions: ProgrammingSubmission[] = [
            { type: SubmissionType.MANUAL, commitHash: 'hash1' },
            { type: SubmissionType.MANUAL, commitHash: 'hash2' },
            { type: SubmissionType.MANUAL, commitHash: 'hash1' }, // Duplicate commit hash
            { type: SubmissionType.INSTRUCTOR, commitHash: 'hash3' }, // Different submission type
        ];

        const mockStudentParticipation: StudentParticipation = {
            submissions: mockSubmissions,
        } as StudentParticipation;

        component.studentParticipation = mockStudentParticipation;
        component.countSubmissions();
        expect(component.numberOfSubmissions).toBe(2);
    });

    it('should call updateSubmissionPolicyItem if submissionPolicy is active and has a submission limit', () => {
        component.submissionPolicy = new LockRepositoryPolicy();
        component.submissionPolicy.active = true;
        component.submissionPolicy.submissionLimit = 5;

        const updateSubmissionPolicyItemSpy = vi.spyOn(component, 'updateSubmissionPolicyItem');

        component.ngOnChanges();

        expect(updateSubmissionPolicyItemSpy).toHaveBeenCalled();
    });

    it('should not make status clickable when there are no sorted history results', () => {
        component.sortedHistoryResults = [];
        component.informationBoxItems = [
            {
                title: 'artemisApp.courseOverview.exerciseDetails.status',
                content: { type: 'submissionStatus', value: exercise },
                isContentComponent: true,
            },
        ];
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const statusDiv = compiled.querySelector('div[class*="pe-auto"]');
        expect(statusDiv).toBeNull();
    });

    it('should make status clickable when there are sorted history results', () => {
        const result = { id: 1, score: 50, submission: { id: 1, participation: { id: 1, type: 'student' } } } as unknown as Result;
        component.sortedHistoryResults = [result];
        component.informationBoxItems = [
            {
                title: 'artemisApp.courseOverview.exerciseDetails.status',
                content: { type: 'submissionStatus', value: exercise },
                isContentComponent: true,
            },
        ];
        fixture.changeDetectorRef.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const statusDiv = compiled.querySelector('[role="button"]');
        expect(statusDiv).toBeTruthy();
    });

    it('should accept sortedHistoryResults input', () => {
        const results = [{ id: 1, score: 80 } as Result, { id: 2, score: 90 } as Result];
        component.sortedHistoryResults = results;
        expect(component.sortedHistoryResults).toEqual(results);
    });

    it('should default sortedHistoryResults to empty array', () => {
        const newFixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
        const newComponent = newFixture.componentInstance;
        expect(newComponent.sortedHistoryResults).toEqual([]);
    });
});
