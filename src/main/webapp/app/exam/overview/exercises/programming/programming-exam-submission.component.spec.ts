import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/overview/exercises/programming/programming-exam-submission.component';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let component: ProgrammingExamSubmissionComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const newExercise = () => {
        return new ProgrammingExercise(new Course(), new ExerciseGroup());
    };

    const newParticipation = () => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.commitHash = 'Hash commit';
        programmingSubmission.buildFailed = false;

        const participation = new ProgrammingExerciseStudentParticipation();
        participation.submissions = [programmingSubmission];

        return participation;
    };

    it('should change state on commit', () => {
        const studentParticipation = newParticipation();
        fixture.componentRef.setInput('studentParticipation', studentParticipation);

        component.onCommitStateChange(CommitState.UNDEFINED);
        expect(component.hasSubmittedOnce).toBeFalse();

        component.onCommitStateChange(CommitState.CLEAN);

        // After the first call with CommitState.CLEAN, component.hasSubmittedOnce must be now true
        expect(component.hasSubmittedOnce).toBeTrue();

        component.onCommitStateChange(CommitState.CLEAN);

        expect(component.studentParticipation().submissions![0].submitted).toBeTrue();
        expect(component.studentParticipation().submissions![0].isSynced).toBeTrue();
    });

    it('should not be synced on file change', () => {
        const studentParticipation = newParticipation();
        fixture.componentRef.setInput('studentParticipation', studentParticipation);

        component.studentParticipation().submissions![0].isSynced = true;
        component.onFileChanged();

        expect(component.studentParticipation().submissions![0].isSynced).toBeFalse();
    });

    it('should get submission', () => {
        const studentParticipation = newParticipation();
        fixture.componentRef.setInput('studentParticipation', studentParticipation);

        expect(component.getSubmission()).toEqual(studentParticipation.submissions![0]);
    });

    it('should return false if no unsaved changes', () => {
        const exercise = newExercise();

        exercise.allowOfflineIde = true;
        exercise.allowOnlineEditor = false;
        fixture.componentRef.setInput('exercise', exercise);

        expect(component.hasUnsavedChanges()).toBeFalse();
    });
});
