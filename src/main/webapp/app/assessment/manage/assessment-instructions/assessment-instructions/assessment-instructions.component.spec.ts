import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { UMLDiagramType } from '@tumaet/apollon';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

describe('AssessmentInstructionsComponent', () => {
    let comp: AssessmentInstructionsComponent;
    let fixture: ComponentFixture<AssessmentInstructionsComponent>;
    let markdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
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
            exampleSolutionModel: JSON.stringify({
                id: 'test-diagram-id',
                version: '4.0.0',
                title: 'Test Diagram',
                type: 'ClassDiagram',
                nodes: [],
                edges: [],
                assessments: {},
            }),
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

        expect(markdownSpy).toHaveBeenCalledTimes(7);
    });

    it('should convert the grading instructions to html', () => {
        const markdownSpy = jest.spyOn(markdownService, 'safeHtmlForMarkdown');

        comp.exerciseInput = { id: 1, type: ExerciseType.PROGRAMMING, gradingInstructions: '# Heading' } as ProgrammingExercise;

        // once for problem statement, once for instructions
        expect(markdownSpy).toHaveBeenCalledTimes(2);
        expect(markdownSpy).toHaveBeenLastCalledWith('# Heading');
    });

    it('should ignore empty grading instructions', () => {
        const markdownSpy = jest.spyOn(markdownService, 'safeHtmlForMarkdown');

        comp.exerciseInput = { id: 1, type: ExerciseType.PROGRAMMING, problemStatement: 'problem', gradingInstructions: undefined } as ProgrammingExercise;

        expect(markdownSpy).toHaveBeenCalledExactlyOnceWith('problem');
    });
});
