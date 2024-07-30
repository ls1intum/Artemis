import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

import { ExerciseHeadersInformationComponent } from 'app/exercises/shared/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { LockRepositoryPolicy } from 'app/entities/submission-policy.model';



describe('ExerciseHeadersInformationComponent', () => {
    let component: ExerciseHeadersInformationComponent;
    let fixture: ComponentFixture<ExerciseHeadersInformationComponent>;
    let exerciseService: ExerciseService;
    let getExerciseDetailsMock: jest.SpyInstance;
    let participation: StudentParticipation;
    let complaintService: ComplaintService;

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
            imports: [ExerciseHeadersInformationComponent, ArtemisTestModule, TranslateModule.forRoot(), NgbTooltipModule],
            providers: [MockProvider(ExerciseService), MockProvider(ComplaintService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
                component = fixture.componentInstance;
                complaintService = TestBed.inject(ComplaintService);
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: { exercise: exercise } }));
                component.exercise = { ...exercise };
                participation = new StudentParticipation(ParticipationType.PROGRAMMING);
                fixture.detectChanges();
            });
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
        fixture.detectChanges();

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
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        const difficultyLevelComponent = compiled.querySelector('jhi-difficulty-level');
        expect(difficultyLevelComponent).toBeTruthy();
    });

    it('should set individualComplaintDueDate if course.maxComplaintTimeDays is defined', () => {
        const course: Course = { id: 1, maxComplaintTimeDays: 7 } as Course;
        const result: Result = { id: 1, completionDate: dayjs().subtract(2, 'day') } as Result;
        const studentParticipation: StudentParticipation = { id: 1, results: [result] } as StudentParticipation;

        component.course = course;
        component.studentParticipation = studentParticipation;

        const expectedDueDate = dayjs().add(7, 'days');
        jest.spyOn(ComplaintService, 'getIndividualComplaintDueDate').mockReturnValue(expectedDueDate);

        if (component.course?.maxComplaintTimeDays) {
            component.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                component.exercise,
                component.course.maxComplaintTimeDays,
                component.studentParticipation?.results?.last(),
                component.studentParticipation,
            );
        }
    });

    it('should add points item to informationBoxItems', () => {
        const maxPoints = 10;
        const pointsItem = { type: 'points', value: maxPoints };

        jest.spyOn(component, 'getPointsItem').mockReturnValue(pointsItem);

        component.informationBoxItems = [];
        component.informationBoxItems.push(component.getPointsItem(maxPoints, 'points'));

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(pointsItem);
    });

    it('should add bonus points item to informationBoxItems', () => {
        const bonusPoints = 5;
        const pointsItem = { type: 'bonus', value: bonusPoints };

        jest.spyOn(component, 'getPointsItem').mockReturnValue(pointsItem);

        component.informationBoxItems = [];
        component.informationBoxItems.push(component.getPointsItem(bonusPoints, 'points'));

        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(pointsItem);
    });

    it('should add start date item to informationBoxItems if startDate is in the future', () => {
        const exercise = {
            id: 43,
            type: ExerciseType.TEXT,
            studentParticipations: [],
            course: {},
            dueDate: dayjs().add(1, 'weeks'),
            assessmentDueDate: dayjs().add(1, 'weeks'),
            startDate: dayjs().add(3, 'days'),
        } as unknown as Exercise;

        component.exercise = { ...exercise };

        const startDateItem = {
            title: 'artemisApp.courseOverview.exerciseDetails.startDate',
            content: {
                type: 'dateTime',
                value: dayjs().add(3, 'days'),
            },
            isContentComponent: true,
        };
        component.informationBoxItems = [];

        fixture.detectChanges();

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
        const assessmentDueItem = {
            title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
            content: {
                type: 'dateTime',
                value: dayjs().add(1, 'weeks'),
            },
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
        // Mock the countSubmissions method
        jest.spyOn(component, 'countSubmissions').mockImplementation(() => {});

        // Mock the getSubmissionPolicyItem method
        const mockSubmissionPolicyItem = { title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle', content: { type: 'text', value: 'Updated Item' } };
        jest.spyOn(component, 'getSubmissionPolicyItem').mockReturnValue(mockSubmissionPolicyItem);

        // Initialize informationBoxItems with a mock item
        component.informationBoxItems = [{ title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle', content: { type: 'text', value: 'Original Item' } }];

        // Call the function
        component.updateSubmissionPolicyItem();

        // Verify that countSubmissions was called
        expect(component.countSubmissions).toHaveBeenCalled();

        // Verify that the item in informationBoxItems was updated
        expect(component.informationBoxItems).toHaveLength(1);
        expect(component.informationBoxItems[0]).toEqual(mockSubmissionPolicyItem);
    });

    it('should correctly count unique manual submissions', () => {
        const mockResults: Result[] = [
            { submission: { type: SubmissionType.MANUAL, commitHash: 'hash1' } as ProgrammingSubmission } as Result,
            { submission: { type: SubmissionType.MANUAL, commitHash: 'hash2' } as ProgrammingSubmission } as Result,
            { submission: { type: SubmissionType.MANUAL, commitHash: 'hash1' } as ProgrammingSubmission } as Result, // Duplicate commit hash
            { submission: { type: SubmissionType.INSTRUCTOR, commitHash: 'hash3' } as ProgrammingSubmission } as Result, // Different submission type
        ];

        const mockStudentParticipation: StudentParticipation = {
            results: mockResults,
        } as StudentParticipation;

        component.studentParticipation = mockStudentParticipation;
        component.countSubmissions();
        expect(component.numberOfSubmissions).toBe(2);
    });


    it('should call updateSubmissionPolicyItem if submissionPolicy is active and has a submission limit', () => {
        component.submissionPolicy = new LockRepositoryPolicy();
        component.submissionPolicy.active = true;
        component.submissionPolicy.submissionLimit = 5;

        const updateSubmissionPolicyItemSpy = jest.spyOn(component, 'updateSubmissionPolicyItem');

        component.ngOnChanges();

        expect(updateSubmissionPolicyItemSpy).toHaveBeenCalled();
    });

    
});
