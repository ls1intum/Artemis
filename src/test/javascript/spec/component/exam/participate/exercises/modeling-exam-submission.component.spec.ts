import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SubmissionVersion } from 'app/entities/submission-version.model';

describe('ModelingExamSubmissionComponent', () => {
    let fixture: ComponentFixture<ModelingExamSubmissionComponent>;
    let comp: ModelingExamSubmissionComponent;

    let mockSubmission: ModelingSubmission;
    let mockExercise: ModelingExercise;

    const resetComponent = () => {
        if (comp) {
            mockSubmission = { explanationText: 'Test Explanation', model: JSON.stringify({ model: true }) } as ModelingSubmission;
            const course = new Course();
            course.isAtLeastInstructor = true;
            mockExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            mockExercise.problemStatement = 'Test Problem Statement';
            comp.exercise = mockExercise;
            comp.studentSubmission = mockSubmission;
        }
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [
                ModelingExamSubmissionComponent,
                MockComponent(ModelingEditorComponent),
                FullscreenComponent,
                ResizeableContainerComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
            ],
            providers: [MockProvider(ChangeDetectorRef)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExamSubmissionComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('With exercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should initialize', () => {
            fixture.detectChanges();
            expect(ModelingExamSubmissionComponent).not.toBeNull();
        });

        it('should show exercise group title', () => {
            comp.exercise.exerciseGroup = { title: 'Test Group' } as ExerciseGroup;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === comp.exercise.exerciseGroup?.title);
            expect(el).not.toBeNull();
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise.maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points]`));
            expect(el).not.toBeNull();
        });

        it('should show exercise bonus score if any', () => {
            const maxScore = 40;
            comp.exercise.maxPoints = maxScore;
            const bonusPoints = 55;
            comp.exercise.bonusPoints = bonusPoints;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) =>
                de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points, ${bonusPoints} artemisApp.examParticipation.bonus]`),
            );
            expect(el).not.toBeNull();
        });

        it('should show modeling editor with correct props when there is submission and exercise', () => {
            fixture.detectChanges();
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
            expect(modelingEditor).not.toBeNull();
            expect(modelingEditor.componentInstance.umlModel).toEqual({ model: true });
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

    describe('without exercise', () => {
        it('should not show anything if no exercise', () => {
            fixture.detectChanges();
            expect(fixture.debugElement.query(() => true)).toBeNull();
        });
    });

    describe('without submission', () => {
        it('should not show anything if no exercise', () => {
            comp.exercise = mockExercise;
            fixture.detectChanges();
            expect(fixture.debugElement.query(() => true)).not.toBeNull();
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
            expect(modelingEditor).toBeNull();
        });
    });

    describe('ngOnInit', () => {
        beforeEach(() => {
            resetComponent();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = jest.spyOn(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).toHaveBeenCalledOnce();
        });
    });

    describe('getSubmission', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return student submission', () => {
            expect(comp.getSubmission()).toEqual(mockSubmission);
        });
    });

    describe('getExercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return exercise', () => {
            expect(comp.getExerciseId()).toEqual(mockExercise.id);
        });
    });

    describe('updateProblemStatement', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should update problem statement', () => {
            const newProblemStatement = 'new problem statement';
            comp.updateProblemStatement(newProblemStatement);
            expect(comp.exercise.problemStatement).toEqual(newProblemStatement);
        });
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should set submission model to new model from modeling editor', () => {
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent)).componentInstance;
            const newModel = { newModel: true };
            const currentModelStub = jest.spyOn(modelingEditor, 'getCurrentModel').mockReturnValue(newModel);
            const explanationText = 'New explanation text';
            comp.explanationText = explanationText;
            comp.updateSubmissionFromView();
            expect(comp.studentSubmission.model).toEqual(JSON.stringify(newModel));
            expect(currentModelStub).toHaveBeenCalledTimes(2);
            expect(comp.studentSubmission.explanationText).toEqual(explanationText);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission.isSynced = false;
            expect(comp.hasUnsavedChanges()).toBeTrue();
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission.isSynced = true;
            expect(comp.hasUnsavedChanges()).toBeFalse();
        });
    });

    describe('modelChanged', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should set isSynced to false', () => {
            comp.studentSubmission.isSynced = true;
            comp.modelChanged({} as UMLModel);
            expect(comp.studentSubmission.isSynced).toBeFalse();
        });
    });

    describe('explanationChanged', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should set explanation text to given value and isSynced to false', () => {
            const explanationText = 'New Explanation Text';
            comp.studentSubmission.isSynced = true;
            comp.explanationChanged(explanationText);
            expect(comp.studentSubmission.isSynced).toBeFalse();
            expect(comp.explanationText).toEqual(explanationText);
        });
    });

    it('should update the model on submission version change', async () => {
        jest.replaceProperty(comp, 'modelingEditor', { apollonEditor: { nextRender: () => {} } as unknown as ApollonEditor } as unknown as ModelingEditorComponent);
        const submissionVersion = {
            content:
                'Model: {"version":"3.0.0","type":"ClassDiagram","size":{"width":220,"height":420},"interactive":{"elements":{},"relationships":{}},"elements":{},"relationships":{},"assessments":{}}; Explanation: explanation',
        } as unknown as SubmissionVersion;
        const parsedModel = {
            version: '3.0.0',
            type: 'ClassDiagram',
            size: { width: 220, height: 420 },
            interactive: { elements: {}, relationships: {} },
            elements: {},
            relationships: {},
            assessments: {},
        } as UMLModel;
        await comp.setSubmissionVersion(submissionVersion);
        await fixture.whenStable();

        expect(comp.submissionVersion).toEqual(submissionVersion);
        expect(comp.umlModel).toEqual(parsedModel);
        expect(comp.explanationText).toBe('explanation');
    });
});
