import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { MockComponent } from 'ng-mocks';

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

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ModelingExamSummaryComponent).not.toBeNull();
    });

    it('should show no submission when there is no uml model', () => {
        fixture.detectChanges();
        const el = fixture.debugElement.query((de) => de.nativeElement.textContent === 'No submission');
        expect(el).not.toBeNull();
        const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
        expect(modelingEditor).toBeNull();
    });

    it('should show modeling editor with correct props when there is submission and exercise', () => {
        const mockSubmission = { explanationText: 'Test Explanation', model: JSON.stringify({ model: true }) } as ModelingSubmission;
        const course = new Course();
        course.isAtLeastInstructor = true;
        comp.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        comp.submission = mockSubmission;
        fixture.detectChanges();
        const modelingEditor = fixture.debugElement.query(By.directive(ModelingEditorComponent));
        expect(modelingEditor).not.toBeNull();
        const modelingEditorComponent = modelingEditor.componentInstance;
        expect(modelingEditorComponent.diagramType).toEqual(UMLDiagramType.ClassDiagram);
        expect(modelingEditorComponent.umlModel).toEqual({ model: true });
        expect(modelingEditorComponent.readOnly).toBeTrue();
        expect(modelingEditorComponent.withExplanation).toEqual(!!mockSubmission.explanationText);
        expect(modelingEditorComponent.explanation).toEqual(mockSubmission.explanationText);
    });
});
