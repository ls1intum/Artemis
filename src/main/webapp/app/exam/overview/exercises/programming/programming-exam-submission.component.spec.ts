import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/overview/exercises/programming/programming-exam-submission.component';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ProgrammingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let component: ProgrammingExamSubmissionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
        expect(component.hasSubmittedOnce).toBe(false);

        component.onCommitStateChange(CommitState.CLEAN);

        // After the first call with CommitState.CLEAN, component.hasSubmittedOnce must be now true
        expect(component.hasSubmittedOnce).toBe(true);

        component.onCommitStateChange(CommitState.CLEAN);

        expect(component.studentParticipation().submissions![0].submitted).toBe(true);
        expect(component.studentParticipation().submissions![0].isSynced).toBe(true);
    });

    it('should not be synced on file change', () => {
        const studentParticipation = newParticipation();
        fixture.componentRef.setInput('studentParticipation', studentParticipation);

        component.studentParticipation().submissions![0].isSynced = true;
        component.onFileChanged();

        expect(component.studentParticipation().submissions![0].isSynced).toBe(false);
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

        expect(component.hasUnsavedChanges()).toBe(false);
    });

    it('should force a re-render of the problem statement on activation to restore PlantUML diagrams', () => {
        // Regression test for the exam bug where switching between exercises removed already-rendered PlantUML diagrams.
        // onActivate() must force a re-render (which bypasses the "unchanged problem statement" optimization) instead of
        // calling updateMarkdown(), so the diagrams are reliably restored when the exercise becomes visible again.
        const forceReRenderProblemStatement = vi.fn();
        const updateMarkdown = vi.fn();
        // Replace the required viewChild signal with a stub instructions component.
        (component as unknown as { instructions: () => unknown }).instructions = () => ({ forceReRenderProblemStatement, updateMarkdown });
        const updateDomainSpy = vi.spyOn(component, 'updateDomain').mockImplementation(() => {});
        const reattachSpy = vi.spyOn((component as unknown as { changeDetectorReference: { reattach: () => void } }).changeDetectorReference, 'reattach');

        component.onActivate();

        // The re-render is forced (not the optimized updateMarkdown which would skip the unchanged statement).
        expect(forceReRenderProblemStatement).toHaveBeenCalledOnce();
        expect(updateMarkdown).not.toHaveBeenCalled();
        // The domain is refreshed and change detection is reattached for the now-visible exercise.
        expect(updateDomainSpy).toHaveBeenCalledOnce();
        expect(reattachSpy).toHaveBeenCalledOnce();
        // Change detection must be reattached (super.onActivate) BEFORE the re-render, so the diagram injection runs
        // against a live, attached component rather than the detached DOM that caused the original bug.
        expect(reattachSpy.mock.invocationCallOrder[0]).toBeLessThan(forceReRenderProblemStatement.mock.invocationCallOrder[0]);
    });

    it('should notify the sync-state version on every isSynced transition', () => {
        // `isSynced` is mutated in place, so under zoneless change detection the exam navigation sidebar and
        // exercise overview only re-evaluate their saved/unsaved icons if the service-wide version signal is
        // bumped. Without these notifications those indicators stay stale for programming exam exercises.
        const examParticipationService = TestBed.inject(ExamParticipationService);
        const notify = vi.spyOn(examParticipationService, 'notifySubmissionSyncStateChanged');
        fixture.componentRef.setInput('studentParticipation', newParticipation());

        component.onFileChanged();
        expect(component.studentParticipation().submissions![0].isSynced).toBe(false);
        expect(notify).toHaveBeenCalledOnce();

        component.hasSubmittedOnce = true;
        component.onCommitStateChange(CommitState.CLEAN);
        expect(component.studentParticipation().submissions![0].isSynced).toBe(true);
        expect(notify).toHaveBeenCalledTimes(2);
    });
});

describe('ProgrammingExamSubmissionComponent offering its toolbar to the title row', () => {
    let fixture: ComponentFixture<ProgrammingExamSubmissionComponent>;
    let offerActions: ReturnType<typeof vi.fn>;
    // the three queries of the component, as signals the tests can change like the view does when an editor comes or goes
    const onlineEditor = signal<{ navbar: () => { nativeElement: HTMLElement } | undefined } | undefined>(undefined);
    const offlineToolbar = signal<{ nativeElement: HTMLElement } | undefined>(undefined);

    beforeEach(() => {
        offerActions = vi.fn();
        onlineEditor.set(undefined);
        offlineToolbar.set(undefined);
        // the template is not rendered: the code editor with its Monaco instance is not needed to see what the component hands to the header
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).overrideComponent(ProgrammingExamSubmissionComponent, { set: { template: '' } });
        fixture = TestBed.createComponent(ProgrammingExamSubmissionComponent);
        const stubs = fixture.componentInstance as unknown as Record<string, unknown>;
        stubs['header'] = () => ({ offerActions });
        stubs['onlineEditor'] = onlineEditor;
        stubs['offlineToolbar'] = offlineToolbar;

        const exercise = new ProgrammingExercise(new Course(), new ExerciseGroup());
        const participation = new ProgrammingExerciseStudentParticipation();
        participation.submissions = [new ProgrammingSubmission()];
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('offers the toolbar of the online editor, which the editor exposes as its navbar', () => {
        const navbar = document.createElement('div');
        onlineEditor.set({ navbar: () => ({ nativeElement: navbar }) });

        fixture.detectChanges();

        expect(offerActions).toHaveBeenCalledExactlyOnceWith(navbar);
    });

    it('offers the row of the Code button and the result when the exercise has no online editor', () => {
        const row = document.createElement('div');
        offlineToolbar.set({ nativeElement: row });

        fixture.detectChanges();

        expect(offerActions).toHaveBeenCalledExactlyOnceWith(row);
    });

    it('offers nothing while neither of them is shown, and follows the editor when it comes and goes', () => {
        fixture.detectChanges();
        expect(offerActions).toHaveBeenLastCalledWith(undefined);

        const navbar = document.createElement('div');
        onlineEditor.set({ navbar: () => ({ nativeElement: navbar }) });
        fixture.detectChanges();
        expect(offerActions).toHaveBeenLastCalledWith(navbar);

        onlineEditor.set(undefined);
        fixture.detectChanges();
        expect(offerActions).toHaveBeenLastCalledWith(undefined);
    });
});
