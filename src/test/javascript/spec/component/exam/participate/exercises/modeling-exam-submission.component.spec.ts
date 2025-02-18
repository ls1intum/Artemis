import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { ExerciseSaveButtonComponent } from 'app/exam/participate/exercises/exercise-save-button/exercise-save-button.component';
import { TranslateDirective } from '../../../../../../../main/webapp/app/shared/language/translate.directive';

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
            imports: [ArtemisTestModule],
            declarations: [
                ModelingExamSubmissionComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
                MockComponent(ExerciseSaveButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [MockProvider(ChangeDetectorRef)],
        }).compileComponents();

        fixture = TestBed.createComponent(ModelingExamSubmissionComponent);
        comp = fixture.componentInstance;
        resetComponent();
    });

    afterEach(() => {
        fixture.destroy();
        jest.restoreAllMocks();
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
            expect(modelingEditor.componentInstance.umlModel).toEqual({ version: '2.0.0', model: true, assessments: {} });
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
            comp.updateProblemStatement(newProblemStatement);
            expect(comp.problemStatementHtml).toEqual(newProblemStatement);
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

    it('should update the model on submission version change', async () => {
        const parsedModel = {
            version: '3.0.0',
            type: 'ClassDiagram',
            size: { width: 220, height: 420 },
            interactive: { elements: {}, relationships: {} },
            elements: {},
            relationships: {},
            assessments: {},
        } as UMLModel;

        jest.spyOn(comp, 'modelingEditor').mockReturnValue({
            apollonEditor: { nextRender: Promise.resolve(), model: parsedModel } as unknown as ApollonEditor,
        } as unknown as ModelingEditorComponent);
        const submissionVersion = {
            content:
                'Model: {"version":"3.0.0","type":"ClassDiagram","size":{"width":220,"height":420},"interactive":{"elements":{},"relationships":{}},"elements":{},"relationships":{},"assessments":{}}; Explanation: explanation',
        } as unknown as SubmissionVersion;
        await comp.setSubmissionVersion(submissionVersion);

        expect(comp.submissionVersion).toEqual(submissionVersion);
        expect(comp.umlModel).toEqual(parsedModel);
        expect(comp.explanationText).toBe('explanation');
    });
});
