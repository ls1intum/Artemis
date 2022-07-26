import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { MockComponent, MockProvider, MockDirective } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/assessment-instructions/expandable-section/expandable-section.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';

describe('AssessmentInstructionsComponent', () => {
    let comp: AssessmentInstructionsComponent;
    let fixture: ComponentFixture<AssessmentInstructionsComponent>;
    let markdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                AssessmentInstructionsComponent,
                MockComponent(ExpandableSectionComponent),
                MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
                MockComponent(ModelingEditorComponent),
                MockDirective(ExtensionPointDirective),
            ],
            providers: [MockProvider(ArtemisMarkdownService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentInstructionsComponent);
                comp = fixture.componentInstance;
                markdownService = TestBed.inject(ArtemisMarkdownService);
            });
    });

    it('should initialize exercise input', () => {
        const modelingExercise = {
            id: 1,
            exampleSolutionModel: '{"elements": [{"id": 1}]}',
            diagramType: UMLDiagramType.ClassDiagram,
            exampleSolutionExplanation: 'explanation',
            type: ExerciseType.MODELING,
        } as ModelingExercise;
        const markdownSpy = jest.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('sample text');
        comp.exerciseInput = modelingExercise;
        expect(comp.sampleSolutionModel).toBeDefined();
        expect(comp.sampleSolutionDiagramType).toBeDefined();
        expect(comp.sampleSolutionExplanation).toBeDefined();

        comp.sampleSolutionExplanation = undefined;
        fixture.detectChanges();
        const textExercise = { id: 1, exampleSolution: 'sample solution', type: ExerciseType.TEXT } as TextExercise;
        comp.exerciseInput = textExercise;
        expect(comp.sampleSolutionExplanation).toBeDefined();

        comp.sampleSolutionExplanation = undefined;
        fixture.detectChanges();
        const fileUploadExercise = { id: 1, exampleSolution: 'sample solution', type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;
        comp.exerciseInput = fileUploadExercise;
        expect(comp.sampleSolutionExplanation).toBeDefined();

        comp.sampleSolutionExplanation = undefined;
        fixture.detectChanges();
        const programmingExercise = { id: 1, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
        comp.exerciseInput = programmingExercise;
        expect(comp.sampleSolutionExplanation).toBeUndefined();

        expect(markdownSpy).toHaveBeenCalledTimes(11);
    });
});
