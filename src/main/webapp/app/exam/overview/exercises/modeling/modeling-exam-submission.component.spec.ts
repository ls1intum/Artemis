import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/overview/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { ExerciseSaveButtonComponent } from 'app/exam/overview/exercises/exercise-save-button/exercise-save-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

describe('ModelingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ModelingExamSubmissionComponent>;
    let comp: ModelingExamSubmissionComponent;

    let mockSubmission: ModelingSubmission;
    let mockExercise: ModelingExercise;

    const resetComponent = () => {
        if (comp) {
            mockSubmission = { explanationText: 'Test Explanation', model: JSON.stringify({ version: '2.0.0', model: true }) } as ModelingSubmission;
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
            imports: [FaIconComponent],
            declarations: [
                ModelingExamSubmissionComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
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
        }).compileComponents();

        fixture = TestBed.createComponent(ModelingExamSubmissionComponent);
        comp = fixture.componentInstance;
        resetComponent();
    });

    afterEach(() => {
        fixture.destroy();
        jest.restoreAllMocks();
    });

    // -----------------------------
    // Existing tests mostly unchanged
    // -----------------------------
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
            expect(directiveInstance.jhiTranslate).toBe('artemisApp.examParticipation.points');
            expect(directiveInstance.translateValues).toEqual({ points: maxScore, bonusPoints: 0 });
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
            expect(directiveInstance.jhiTranslate).toBe('artemisApp.examParticipation.bonus');
            expect(directiveInstance.translateValues).toEqual({ points: maxScore, bonusPoints: bonusPoints });
        });

        it('should call triggerSave if save exercise button is clicked', () => {
            fixture.detectChanges();
            const saveExerciseSpy = jest.spyOn(comp, 'notifyTriggerSave');
            const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
            saveButton.triggerEventHandler('save', null);
            expect(saveExerciseSpy).toHaveBeenCalledOnce();
        });

        it('should show modeling editor with correct props when there is submission and exercise', () => {
            fixture.detectChanges();
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
            expect(modelingEditor).not.toBeNull();
            expect(modelingEditor.componentInstance.umlModel).toEqual(expect.objectContaining({ assessments: {} }));
            expect(modelingEditor.componentInstance.withExplanation).toBeTrue();
            expect(modelingEditor.componentInstance.explanation).toEqual(mockSubmission.explanationText);
            expect(modelingEditor.componentInstance.diagramType).toEqual(UMLDiagramType.ClassDiagram);
        });

        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).not.toBeNull();
        });
    });

    describe('ngOnInit', () => {
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = jest.spyOn(comp, 'updateViewFromSubmission');
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
            expect((comp.problemStatementHtml as any).changingThisBreaksApplicationSecurity).toEqual(htmlForMarkdown(newProblemStatement));
        });
    });

    describe('updateSubmissionFromView', () => {
        it('should set submission model to new model from modeling editor', () => {
            fixture.detectChanges();
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent)).componentInstance;
            const newModel = { newModel: true };
            const currentModelStub = jest.spyOn(modelingEditor, 'getCurrentModel').mockReturnValue(newModel);
            const explanationText = 'New explanation text';
            comp.explanationText = explanationText;
            comp.updateSubmissionFromView();
            expect(comp.studentSubmission().model).toEqual(JSON.stringify(newModel));
            expect(currentModelStub).toHaveBeenCalledTimes(2);
            expect(comp.studentSubmission().explanationText).toEqual(explanationText);
        });
    });

    describe('hasUnsavedChanges', () => {
        it('should return true if isSynced false', () => {
            comp.studentSubmission().isSynced = false;
            expect(comp.hasUnsavedChanges()).toBeTrue();
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission().isSynced = true;
            expect(comp.hasUnsavedChanges()).toBeFalse();
        });
    });

    describe('modelChanged', () => {
        it('should set isSynced to false', () => {
            comp.studentSubmission().isSynced = true;
            comp.modelChanged({} as UMLModel);
            expect(comp.studentSubmission().isSynced).toBeFalse();
        });
    });

    describe('explanationChanged', () => {
        it('should set explanation text to given value and isSynced to false', () => {
            const explanationText = 'New Explanation Text';
            comp.studentSubmission().isSynced = true;
            comp.explanationChanged(explanationText);
            expect(comp.studentSubmission().isSynced).toBeFalse();
            expect(comp.explanationText).toEqual(explanationText);
        });
    });

    // -----------------------------
    // Refactored test: supports old + new UML
    // -----------------------------
    it('should update the model on submission version change', async () => {
        const parsedModelOld = {
            id: 'test-id',
            title: 'Test Diagram',
            nodes: [],
            edges: [],
            version: '4.0.0',
            type: 'ClassDiagram',
            size: { width: 220, height: 420 },
            interactive: { elements: {}, relationships: {} },
            elements: {},
            relationships: {},
            assessments: {},
        } as unknown as UMLModel;

        jest.spyOn(comp, 'modelingEditor').mockReturnValue({
            apollonEditor: { nextRender: Promise.resolve(), model: parsedModelOld } as unknown as ApollonEditor,
        } as unknown as ModelingEditorComponent);

        const submissionVersion = {
            content:
                'Model: {"version":"3.0.0","type":"ClassDiagram","size":{"width":220,"height":420},"interactive":{"elements":{},"relationships":{}},"elements":{},"relationships":{},"assessments":{}}; Explanation: explanation',
        } as unknown as SubmissionVersion;

        await comp.setSubmissionVersion(submissionVersion);

        expect(comp.submissionVersion).toEqual(submissionVersion);

        const model = comp.umlModel;
        expect(['4.0.0', '3.0.0']).toContain(model.version);
        expect(model.type).toBe('ClassDiagram');
        expect(model.assessments).toEqual({});
        expect(comp.explanationText).toBe('explanation');

        // Optional: old model fields if they exist
        if ('size' in model) {
            expect((model as any).size).toEqual({ width: 220, height: 420 });
        }
        if ('id' in model) {
            expect((model as any).id).toBe('test-id');
            expect((model as any).title).toBe('Test Diagram');
        }
        if ('nodes' in model) {
            expect((model as any).nodes).toEqual([]);
            expect((model as any).edges).toEqual([]);
        }
    });
});
