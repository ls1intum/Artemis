import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import * as chai from 'chai';
import { MockComponent } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ModelingExamSummaryComponent', () => {
    let fixture: ComponentFixture<ModelingExamSummaryComponent>;
    let comp: ModelingExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ModelingExamSummaryComponent, MockComponent(ModelingEditorComponent)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingExamSummaryComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExamSummaryComponent).to.be.ok;
    });

    it('should show no submission when there is no uml model', () => {
        comp.ngOnInit();
        fixture.detectChanges();
        const el = fixture.debugElement.query((de) => de.nativeElement.textContent === 'No submission');
        expect(el).to.be.not.null;
        const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
        expect(modelingEditor).to.not.exist;
    });

    it('should show modeling editor with correct props when there is submission and exercise', () => {
        const mockSubmission = { explanationText: 'Test Explanation', model: JSON.stringify({ model: true }) } as ModelingSubmission;
        const course = new Course();
        course.isAtLeastInstructor = true;
        comp.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        comp.submission = mockSubmission;
        comp.ngOnInit();
        fixture.detectChanges();
        const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
        expect(modelingEditor).to.exist;
        expect(modelingEditor.componentInstance.umlModel).to.deep.equal({ model: true });
        expect(modelingEditor.componentInstance.readOnly).to.equal(true);
        expect(modelingEditor.componentInstance.withExplanation).to.equal(mockSubmission.explanationText);
        expect(modelingEditor.componentInstance.explanation).to.equal(mockSubmission.explanationText);
        expect(modelingEditor.componentInstance.diagramType).to.equal(UMLDiagramType.ClassDiagram);
    });
});
