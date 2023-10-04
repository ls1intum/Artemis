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

const user = { id: 1, name: 'Test User' } as User;

const exam = {
    id: 1,
    title: 'ExamForTesting',
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
    repositoryUrl: 'https://bitbucket.ase.in.tum.de/projects/TEST/repos/test-exercise',
} as ProgrammingExerciseStudentParticipation;
const programmingExercise = {
    id: 4,
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    exerciseGroup,
    projectKey: 'TEST',
} as ProgrammingExercise;

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
            providers: [MockProvider(ExerciseService), MockProvider(ExerciseCacheService), { provide: ProfileService, useValue: new MockProfileService() }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSummaryComponent);
                component = fixture.componentInstance;
                component.exercise = programmingExercise;
                component.participation = programmingParticipation;
                component.submission = programmingSubmission;

                profileService = TestBed.inject(ProfileService);

                const commitHashURLTemplate = 'https://bitbucket.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}';
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
        expect(component.commitUrl).toBe('https://bitbucket.ase.in.tum.de/projects/test/repos/test-student1/commits/123456789ab');
    });
});
