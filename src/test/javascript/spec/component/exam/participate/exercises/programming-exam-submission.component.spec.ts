import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let component: ProgrammingExamSubmissionComponent;

    let studentParticipation: ProgrammingExerciseStudentParticipation;
    let exercise: ProgrammingExercise;
    let programmingSubmission: ProgrammingSubmission;

    let domainService: any;

    beforeEach(() => {
        programmingSubmission = { commitHash: 'Hash commit', buildFailed: false, buildArtifact: false };
        studentParticipation = { submissions: [programmingSubmission] };
        exercise = new ProgrammingExercise(new Course(), new ExerciseGroup());

        return TestBed.configureTestingModule({
            imports: [
                MockModule(ArtemisExerciseButtonsModule),
                MockModule(ArtemisProgrammingExerciseActionsModule),
                MockModule(ArtemisResultModule),
                MockModule(ArtemisProgrammingExerciseInstructionsRenderModule),
            ],
            declarations: [
                ProgrammingExamSubmissionComponent,
                MockComponent(ModelingEditorComponent),
                MockComponent(CodeEditorContainerComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(IncludedInScoreBadgeComponent),
            ],
            providers: [MockProvider(DomainService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
                component = fixture.componentInstance;
                domainService = TestBed.inject(DomainService);
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('should initialize with unlocked repository', () => {
        const domainServiceSpy = sinon.spy(domainService, 'setDomain');
        component.exercise = exercise;

        fixture.detectChanges();

        expect(fixture).to.be.ok;
        expect(domainServiceSpy.calledOnce);
        expect(component.repositoryIsLocked).to.equal(false);
        expect(component.getExercise()).to.equal(exercise);
    });

    it('should set the repositoryIsLocked value to true', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), new ExerciseGroup());
        programmingExercise.dueDate = moment().subtract(10, 'seconds');
        programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = moment().subtract(60, 'seconds');

        component.exercise = programmingExercise;
        fixture.detectChanges();

        expect(component.repositoryIsLocked).to.equal(true);
    });

    it('should change state on commit', () => {
        component.studentParticipation = studentParticipation;

        const undefinedCommitState = CommitState.UNDEFINED;
        component.onCommitStateChange(undefinedCommitState);
        expect(component.hasSubmittedOnce).to.equal(false);

        const cleanCommitState = CommitState.CLEAN;

        component.onCommitStateChange(cleanCommitState);
        expect(component.hasSubmittedOnce).to.equal(true);

        /**
         * After the first call with CommitState.CLEAN, component.hasSubmittedOnce must be now true
         */
        component.onCommitStateChange(cleanCommitState);
        if (component.studentParticipation.submissions) {
            expect(component.studentParticipation.submissions[0].submitted).to.equal(true);
            expect(component.studentParticipation.submissions[0].isSynced).to.equal(true);
        }
    });

    it('should desync on file change', () => {
        component.studentParticipation = studentParticipation;
        if (component.studentParticipation.submissions) {
            component.studentParticipation.submissions[0].isSynced = true;
            component.onFileChanged();

            expect(component.studentParticipation.submissions[0].isSynced).to.equal(false);
        }
    });

    it('should get submission', () => {
        component.studentParticipation = studentParticipation;
        if (studentParticipation.submissions) {
            expect(component.getSubmission()).to.equal(studentParticipation.submissions[0]);
        }
    });

    it('should return false if no unsaved changes', () => {
        exercise.allowOfflineIde = true;
        exercise.allowOnlineEditor = false;
        component.exercise = exercise;

        expect(component.hasUnsavedChanges()).to.equal(false);
    });
});
