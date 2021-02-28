import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { UMLModel } from '@ls1intum/apollon';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';

chai.use(sinonChai);
const expect = chai.expect;

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
            imports: [ArtemisTestModule],
            declarations: [
                ModelingExamSubmissionComponent,
                MockComponent(ModelingEditorComponent),
                FullscreenComponent,
                ResizeableContainerComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
                MockDirective(NgbTooltip),
                MockComponent(IncludedInScoreBadgeComponent),
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
            expect(ModelingExamSubmissionComponent).to.be.ok;
        });

        it('should show exercise title if any', () => {
            comp.exercise.title = 'Test Title';
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === comp.exercise.title);
            expect(el).to.exist;
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise.maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points]`));
            expect(el).to.exist;
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
            expect(el).to.exist;
        });

        it('should show modeling editor with correct props when there is submission and exercise', () => {
            fixture.detectChanges();
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
            expect(modelingEditor).to.exist;
            expect(modelingEditor.componentInstance.umlModel).to.deep.equal({ model: true });
            expect(modelingEditor.componentInstance.withExplanation).to.equal(true);
            expect(modelingEditor.componentInstance.explanation).to.equal(mockSubmission.explanationText);
            expect(modelingEditor.componentInstance.diagramType).to.equal(UMLDiagramType.ClassDiagram);
        });

        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).to.exist;
        });
    });

    describe('without exercise', () => {
        it('should not show anything if no exercise', () => {
            fixture.detectChanges();
            expect(fixture.debugElement.query(() => true)).to.not.exist;
        });
    });

    describe('without submission', () => {
        it('should not show anything if no exercise', () => {
            comp.exercise = mockExercise;
            fixture.detectChanges();
            expect(fixture.debugElement.query(() => true)).to.exist;
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
            expect(modelingEditor).to.not.exist;
        });
    });

    describe('ngOnInit', () => {
        beforeEach(() => {
            resetComponent();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = sinon.stub(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).to.have.been.called;
        });
    });

    describe('getSubmission', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return student submission', () => {
            expect(comp.getSubmission()).to.deep.equal(mockSubmission);
        });
    });

    describe('getExercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return exercise', () => {
            expect(comp.getExercise()).to.deep.equal(mockExercise);
        });
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should set submission model to new model from modeling editor', () => {
            const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent)).componentInstance;
            const newModel = { newModel: true };
            const currentModelStub = sinon.stub(modelingEditor, 'getCurrentModel').returns(newModel);
            const explanationText = 'New explanation text';
            comp.explanationText = explanationText;
            comp.updateSubmissionFromView();
            expect(comp.studentSubmission.model).to.deep.equal(JSON.stringify(newModel));
            expect(currentModelStub).to.have.been.called;
            expect(comp.studentSubmission.explanationText).to.equal(explanationText);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission.isSynced = false;
            expect(comp.hasUnsavedChanges()).to.equal(true);
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission.isSynced = true;
            expect(comp.hasUnsavedChanges()).to.equal(false);
        });
    });

    describe('modelChanged', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should set isSynced to false', () => {
            comp.studentSubmission.isSynced = true;
            comp.modelChanged({} as UMLModel);
            expect(comp.studentSubmission.isSynced).to.equal(false);
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
            expect(comp.studentSubmission.isSynced).to.equal(false);
            expect(comp.explanationText).to.equal(explanationText);
        });
    });
});
