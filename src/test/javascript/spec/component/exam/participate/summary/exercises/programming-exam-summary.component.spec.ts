import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ProgrammingExamSummaryComponent } from 'app/exam/participate/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { User } from 'app/core/user/user.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { BehaviorSubject } from 'rxjs';
import { MockProfileService } from '../../../../../helpers/mocks/service/mock-profile.service';
import { SubmissionType } from 'app/entities/submission.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

const user = { id: 1, name: 'Test User' } as User;

const exam = {
    id: 1,
    title: 'ExamForTesting',
    latestIndividualEndDate: dayjs().subtract(10, 'minutes'),
} as Exam;

const exerciseGroup = {
    exam,
    title: 'exercise group',
} as ExerciseGroup;

const programmingSubmission = {
    id: 1,
    type: SubmissionType.MANUAL,
    commitHash: '123456789ab',
} as ProgrammingSubmission;

const programmingParticipation = {
    id: 4,
    student: user,
    submissions: [programmingSubmission],
    type: ParticipationType.PROGRAMMING,
    participantIdentifier: 'student1',
    repositoryUri: 'https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username',
} as ProgrammingExerciseStudentParticipation;

const programmingExercise = {
    id: 4,
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    exerciseGroup,
    projectKey: 'TEST',
    dueDate: dayjs().subtract(5, 'minutes'),
} as ProgrammingExercise;

const feedbackReference = {
    id: 1,
    result: { id: 2 } as Result,
    hasLongFeedback: false,
} as Feedback;

const feedback = {
    type: 'Test',
    name: 'artemisApp.result.detail.feedback',
    title: 'artemisApp.result.detail.test.passedTest',
    positive: true,
    credits: 3,
    feedbackReference,
};

const result = {
    id: 89,
    participation: {
        id: 55,
        type: ParticipationType.PROGRAMMING,
        participantIdentifier: 'student42',
        repositoryUri: 'https://gitlab.ase.in.tum.de/projects/somekey/repos/somekey-student42',
    },
    feedbacks: [feedback],
    assessmentType: AssessmentType.MANUAL,
} as Result;

describe('ProgrammingExamSummaryComponent', () => {
    let component: ProgrammingExamSummaryComponent;
    let fixture: ComponentFixture<ProgrammingExamSummaryComponent>;

    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                ProgrammingExamSummaryComponent,
                MockComponent(CloneRepoButtonComponent),
                MockComponent(FeedbackComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(ExerciseService),
                MockProvider(ExerciseCacheService),
                {
                    provide: ProfileService,
                    useValue: new MockProfileService(),
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSummaryComponent);
                component = fixture.componentInstance;

                component.exercise = programmingExercise;
                programmingParticipation.results = [result];
                component.participation = programmingParticipation;
                component.submission = programmingSubmission;
                component.exam = exam;

                profileService = TestBed.inject(ProfileService);

                const commitHashURLTemplate = 'https://gitlab.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}';
                const profileInfo = new ProfileInfo();
                profileInfo.commitHashURLTemplate = commitHashURLTemplate;
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new BehaviorSubject(profileInfo));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeTruthy();
    });

    it('should set commitUrl', () => {
        const spyOnGetProfileInfo = jest.spyOn(profileService, 'getProfileInfo');

        fixture.detectChanges();

        expect(spyOnGetProfileInfo).toHaveBeenCalledOnce();
        expect(component.commitHash).toBe('123456789ab');
        expect(component.commitUrl).toBe('https://gitlab.ase.in.tum.de/projects/test/repos/test-student1/commits/123456789ab');
    });

    it('should show result if present and results are published', () => {
        component.isAfterResultsArePublished = true;

        fixture.detectChanges();

        expect(component.feedbackComponentParameters.exercise).toEqual(programmingExercise);
        expect(component.feedbackComponentParameters.result).toEqual(result);
        expect(component.feedbackComponentParameters.exerciseType).toEqual(programmingExercise.type);

        const modelingSubmissionComponent = fixture.debugElement.query(By.directive(FeedbackComponent))?.componentInstance;
        expect(modelingSubmissionComponent).toBeTruthy();
    });

    it('should not show results if not yet published', () => {
        component.isAfterResultsArePublished = false;

        fixture.detectChanges();

        const modelingSubmissionComponent = fixture.debugElement.query(By.directive(FeedbackComponent))?.componentInstance;
        expect(modelingSubmissionComponent).not.toBeTruthy();
    });

    it('should display clone button', () => {
        const modelingSubmissionComponent = fixture.debugElement.query(By.directive(CloneRepoButtonComponent))?.componentInstance;
        expect(modelingSubmissionComponent).toBeTruthy();
    });
});
