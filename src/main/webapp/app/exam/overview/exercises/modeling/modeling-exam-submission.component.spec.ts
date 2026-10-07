import { ChangeDetectorRef, Component, input, model } from '@angular/core';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { Course } from 'app/course/shared/entities/course.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Stub for ModelingEditorComponent to avoid Apollon editor initialization issues
@Component({
    selector: 'jhi-modeling-editor',
    template: '',
})
class StubModelingEditorComponent {
    tile = input(false);
    umlModel = input<UMLModel>();
    diagramType = input<UMLDiagramType>();
    problemStatement = input<string>();
    readOnly = input(false);
    withExplanation = input(false);
    savedStatus = input<{ isChanged?: boolean; isSaving?: boolean }>();
    explanation = model<string>('');

    getCurrentModel(): UMLModel {
        return this.umlModel() ?? ({} as UMLModel);
    }
}
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/overview/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { ExerciseSaveButtonComponent } from 'app/exam/overview/exercises/exercise-save-button/exercise-save-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';

describe('ModelingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ModelingExamSubmissionComponent>;
    let comp: ModelingExamSubmissionComponent;

    let mockSubmission: ModelingSubmission;
    let mockExercise: ModelingExercise;
    const editedModel = { nodes: [{ id: 'new-class' }], edges: [] } as unknown as UMLModel;

    const resetComponent = () => {
        if (comp) {
            mockSubmission = {
                explanationText: 'Test Explanation',
                model: JSON.stringify({
                    version: '3.0.0',
                    type: 'ClassDiagram',
                    size: { width: 200, height: 200 },
                    interactive: { elements: {}, relationships: {} },
                    elements: {},
                    relationships: {},
                    assessments: {},
                }),
            } as ModelingSubmission;
            const course = new Course();
            course.isAtLeastInstructor = true;
            mockExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            mockExercise.problemStatement = 'Test Problem Statement';
            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('studentSubmission', mockSubmission);
        }
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                StubModelingEditorComponent,
                ModelingExamSubmissionComponent,
                TranslatePipeMock,
                MockDirective(MarkdownDirective),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
                MockComponent(ExerciseSaveButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(ModelingExamSubmissionComponent, {
                remove: { imports: [ModelingEditorComponent] },
                add: { imports: [StubModelingEditorComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExamSubmissionComponent);
        comp = fixture.componentInstance;
        resetComponent();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    describe('With exercise', () => {
        it('should initialize', () => {
            expect(ModelingExamSubmissionComponent).not.toBeNull();
        });

        it('should show static text in header', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.css('.exercise-title'));
            expect(el).not.toBeNull();
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise().maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.directive(TranslateDirective));
            expect(el).not.toBeNull();

            const directiveInstance = el.injector.get(TranslateDirective);
            expect(directiveInstance.jhiTranslate()).toBe('artemisApp.examParticipation.points');
            expect(directiveInstance.translateValues()).toEqual({ points: maxScore, bonusPoints: 0 });
        });

        it('should show exercise bonus score if any', () => {
            const maxScore = 40;
            comp.exercise().maxPoints = maxScore;
            const bonusPoints = 55;
            comp.exercise().bonusPoints = bonusPoints;
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.directive(TranslateDirective));
            expect(el).not.toBeNull();

            const directiveInstance = el.injector.get(TranslateDirective);
            expect(directiveInstance.jhiTranslate()).toBe('artemisApp.examParticipation.bonus');
            expect(directiveInstance.translateValues()).toEqual({ points: maxScore, bonusPoints: bonusPoints });
        });

        it('should call triggerSave if save exercise button is clicked', () => {
            fixture.detectChanges();
            const saveExerciseSpy = vi.spyOn(comp, 'notifyTriggerSave');
            const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
            saveButton.triggerEventHandler('save', null);
            expect(saveExerciseSpy).toHaveBeenCalledOnce();
        });

        it('should show modeling editor with correct props when there is submission and exercise', () => {
            fixture.detectChanges();
            const modelingEditor = fixture.debugElement.query(By.directive(StubModelingEditorComponent));
            expect(modelingEditor).not.toBeNull();
            const umlModel = modelingEditor.componentInstance.umlModel();
            expect(umlModel).toBeDefined();
            expect(umlModel.version).toMatch(/^4\.\d+\.\d+$/);
            expect(umlModel.type).toBe('ClassDiagram');
            expect(modelingEditor.componentInstance.withExplanation()).toBe(true);
            expect(modelingEditor.componentInstance.explanation()).toEqual(mockSubmission.explanationText);
            expect(modelingEditor.componentInstance.diagramType()).toEqual(UMLDiagramType.ClassDiagram);
            expect(modelingEditor.componentInstance.problemStatement()).toBe(mockExercise.problemStatement);
        });

        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).not.toBeNull();
        });
    });

    describe('ngOnInit', () => {
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = vi.spyOn(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).toHaveBeenCalledOnce();
        });
    });

    describe('getSubmission', () => {
        it('should return student submission', () => {
            expect(comp.getSubmission()).toEqual(mockSubmission);
        });
    });

    describe('getExercise', () => {
        it('should return exercise', () => {
            expect(comp.getExerciseId()).toEqual(mockExercise.id);
        });
    });

    describe('updateProblemStatement', () => {
        it('should update problem statement', () => {
            const newProblemStatement = 'new problem statement';
            comp.updateProblemStatement(TestBed.inject(ArtemisMarkdownService).safeHtmlForMarkdown(newProblemStatement));
            expect((comp.problemStatementHtml() as any).changingThisBreaksApplicationSecurity).toEqual(htmlForMarkdown(newProblemStatement));
        });
    });

    describe('updateSubmissionFromView', () => {
        it('should set submission model to new model from modeling editor', () => {
            fixture.detectChanges();
            const modelingEditorElement = fixture.debugElement.query(By.directive(StubModelingEditorComponent));
            const stubModelingEditor = modelingEditorElement.componentInstance as StubModelingEditorComponent;
            const newModel = { newModel: true };
            const currentModelStub = vi.spyOn(stubModelingEditor, 'getCurrentModel').mockReturnValue(newModel as unknown as UMLModel);
            // Mock the viewChild to return the stub
            vi.spyOn(comp, 'modelingEditor').mockReturnValue(stubModelingEditor as unknown as ModelingEditorComponent);
            const explanationText = 'New explanation text';
            comp.explanationText.set(explanationText);
            comp.updateSubmissionFromView();
            expect(comp.studentSubmission().model).toEqual(JSON.stringify(newModel));
            expect(currentModelStub).toHaveBeenCalledOnce();
            expect(comp.studentSubmission().explanationText).toEqual(explanationText);
        });
    });

    describe('hasUnsavedChanges', () => {
        it('should return true if isSynced false', () => {
            comp.studentSubmission().isSynced = false;
            expect(comp.hasUnsavedChanges()).toBe(true);
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission().isSynced = true;
            expect(comp.hasUnsavedChanges()).toBe(false);
        });
    });

    describe('modelChanged', () => {
        it('should set isSynced to false', () => {
            comp.studentSubmission().isSynced = true;
            comp.modelChanged(editedModel);
            expect(comp.studentSubmission().isSynced).toBe(false);
        });

        it('should keep the submission synced when Apollon reports the loaded diagram again (selection change)', () => {
            mockSubmission.isSynced = true;
            fixture.detectChanges();
            const notify = vi.spyOn(TestBed.inject(ExamParticipationService), 'notifySubmissionSyncStateChanged');

            comp.modelChanged(comp.umlModel());

            expect(mockSubmission.isSynced).toBe(true);
            expect(notify).not.toHaveBeenCalled();
        });

        it('should keep a submission without a model synced when Apollon reports the empty diagram', () => {
            mockSubmission.model = undefined;
            mockSubmission.isSynced = true;
            fixture.detectChanges();

            comp.modelChanged({ nodes: [], edges: [] } as unknown as UMLModel);

            expect(mockSubmission.isSynced).toBe(true);
        });

        it('should treat the diagram handed to a save as the new baseline', () => {
            mockSubmission.isSynced = true;
            fixture.detectChanges();
            const stub = fixture.debugElement.query(By.directive(StubModelingEditorComponent)).componentInstance as StubModelingEditorComponent;
            vi.spyOn(comp, 'modelingEditor').mockReturnValue(stub as unknown as ModelingEditorComponent);
            vi.spyOn(stub, 'getCurrentModel').mockReturnValue(editedModel);

            comp.modelChanged(editedModel);
            expect(mockSubmission.isSynced).toBe(false);

            comp.updateSubmissionFromView();
            mockSubmission.isSynced = true;
            comp.modelChanged(editedModel);

            expect(mockSubmission.isSynced).toBe(true);
        });
    });

    describe('explanationChanged', () => {
        it('should set explanation text to given value and isSynced to false', () => {
            const explanationText = 'New Explanation Text';
            comp.studentSubmission().isSynced = true;
            comp.explanationChanged(explanationText);
            expect(comp.studentSubmission().isSynced).toBe(false);
            expect(comp.explanationText()).toEqual(explanationText);
        });
    });

    it('should update the model synchronously on submission version change', () => {
        vi.spyOn(comp, 'modelingEditor').mockReturnValue({
            apollonEditor: { nextRender: Promise.resolve(), model: {} } as unknown as ApollonEditor,
        } as unknown as ModelingEditorComponent);
        const submissionVersion = {
            content:
                'Model: {"version":"3.0.0","type":"ClassDiagram","size":{"width":220,"height":420},"interactive":{"elements":{},"relationships":{}},"elements":{},"relationships":{},"assessments":{}}; Explanation: explanation',
        } as unknown as SubmissionVersion;
        expect(comp.setSubmissionVersion(submissionVersion)).toBeUndefined();

        expect(comp.submissionVersion).toEqual(submissionVersion);
        expect(comp.umlModel()).toBeDefined();
        expect(comp.umlModel()!.version).toMatch(/^4\.\d+\.\d+$/);
        expect(comp.umlModel()!.type).toBe('ClassDiagram');
        expect(comp.explanationText()).toBe('explanation');
    });

    it('should notify the sync-state version whenever it marks the submission unsaved', () => {
        // `isSynced` is mutated in place, so under zoneless change detection the exam navigation sidebar and
        // exercise overview only re-evaluate their saved/unsaved icons if this notification fires. Without it a
        // student editing this exercise type during an exam sees no unsaved-changes indicator.
        resetComponent();
        const examParticipationService = TestBed.inject(ExamParticipationService);
        const notify = vi.spyOn(examParticipationService, 'notifySubmissionSyncStateChanged');

        comp.modelChanged(editedModel);
        comp.explanationChanged('some explanation');

        expect(comp.studentSubmission().isSynced).toBe(false);
        expect(notify).toHaveBeenCalledTimes(2);
    });

    describe('overlay save status', () => {
        // the status handed to the editor's "All changes saved" overlay must agree with the exam sidebar icon and the save button
        const overlayStatus = () => fixture.debugElement.query(By.directive(StubModelingEditorComponent)).componentInstance.savedStatus();

        it('should show unsaved changes as soon as the model is edited and all saved once the save succeeded', () => {
            const examParticipationService = TestBed.inject(ExamParticipationService);
            mockSubmission.isSynced = true;
            fixture.detectChanges();
            expect(overlayStatus()).toEqual({ isChanged: false, isSaving: false });

            comp.modelChanged(editedModel);
            fixture.detectChanges();
            expect(overlayStatus()).toEqual({ isChanged: true, isSaving: false });

            examParticipationService.setSubmissionSaving(mockSubmission, true);
            fixture.detectChanges();
            expect(overlayStatus()).toEqual({ isChanged: true, isSaving: true });

            // what the exam participation does when the save succeeded
            examParticipationService.setSubmissionSaving(mockSubmission, false);
            mockSubmission.isSynced = true;
            examParticipationService.notifySubmissionSyncStateChanged();
            fixture.detectChanges();
            expect(overlayStatus()).toEqual({ isChanged: false, isSaving: false });
        });

        it('should show unsaved changes as soon as the explanation text is edited', () => {
            mockSubmission.isSynced = true;
            fixture.detectChanges();

            comp.explanationChanged('another explanation');
            fixture.detectChanges();

            expect(overlayStatus()).toEqual({ isChanged: true, isSaving: false });
        });

        it('should hand the editor the same status object while the sync state did not change', () => {
            mockSubmission.isSynced = true;
            fixture.detectChanges();
            const status = overlayStatus();

            TestBed.inject(ExamParticipationService).notifySubmissionSyncStateChanged();
            fixture.detectChanges();

            expect(overlayStatus()).toBe(status);
        });
    });
});
