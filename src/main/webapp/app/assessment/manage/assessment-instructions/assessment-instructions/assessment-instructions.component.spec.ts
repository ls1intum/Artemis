import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { UMLDiagramType } from '@tumaet/apollon';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { Component, input, output } from '@angular/core';

// Stub for ModelingEditorComponent
@Component({
    selector: 'jhi-modeling-editor',
    template: '',
    standalone: true,
})
export class StubModelingEditorComponent {
    umlModel = input<unknown>();
    diagramType = input<unknown>();
    readOnly = input<boolean>(false);
    resizeOptions = input<unknown>();
    withExplanation = input<boolean>(false);

    onModelChanged = output<unknown>();

    apollonEditor = {
        nextRender: Promise.resolve(),
    };

    getCurrentModel() {
        return {
            elements: {},
            relationships: {},
            version: '3.0.0',
        };
    }
}

describe('AssessmentInstructionsComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: AssessmentInstructionsComponent;
    let fixture: ComponentFixture<AssessmentInstructionsComponent>;
    let markdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(ArtemisMarkdownService)],
        })
            .overrideComponent(AssessmentInstructionsComponent, {
                remove: {
                    imports: [
                        ExpandableSectionComponent,
                        StructuredGradingInstructionsAssessmentLayoutComponent,
                        ProgrammingExerciseInstructionComponent,
                        SecureLinkDirective,
                        ButtonComponent,
                        TranslateDirective,
                        ModelingEditorComponent,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(ExpandableSectionComponent),
                        MockComponent(StructuredGradingInstructionsAssessmentLayoutComponent),
                        MockComponent(ProgrammingExerciseInstructionComponent),
                        MockDirective(SecureLinkDirective),
                        MockComponent(ButtonComponent),
                        MockDirective(TranslateDirective),
                        StubModelingEditorComponent,
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentInstructionsComponent);
                comp = fixture.componentInstance;
                markdownService = TestBed.inject(ArtemisMarkdownService);
            });
    });

    it('should initialize exercise input for modeling exercise', () => {
        const modelingExercise = {
            id: 1,
            exampleSolutionModel: '{"elements": [{"id": 1}]}',
            diagramType: UMLDiagramType.ClassDiagram,
            exampleSolutionExplanation: 'explanation',
            type: ExerciseType.MODELING,
        } as ModelingExercise;
        vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('sample text');
        fixture.componentRef.setInput('exercise', modelingExercise);
        fixture.detectChanges();

        expect(comp.sampleSolutionModel()).toBeDefined();
        expect(comp.sampleSolutionDiagramType()).toBeDefined();
        expect(comp.sampleSolutionExplanation()).toBeDefined();
    });

    it('should initialize exercise input for text exercise', () => {
        vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('sample text');
        const textExercise = { id: 1, exampleSolution: 'sample solution', type: ExerciseType.TEXT } as TextExercise;
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.detectChanges();

        expect(comp.sampleSolutionExplanation()).toBeDefined();
    });

    it('should initialize exercise input for file upload exercise', () => {
        vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('sample text');
        const fileUploadExercise = { id: 1, exampleSolution: 'sample solution', type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;
        fixture.componentRef.setInput('exercise', fileUploadExercise);
        fixture.detectChanges();

        expect(comp.sampleSolutionExplanation()).toBeDefined();
    });

    it('should not have sample solution explanation for programming exercise', () => {
        vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('sample text');
        const programmingExercise = { id: 1, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
        fixture.componentRef.setInput('exercise', programmingExercise);
        fixture.detectChanges();

        expect(comp.sampleSolutionExplanation()).toBeUndefined();
    });

    it('should convert the grading instructions to html', () => {
        const markdownSpy = vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('converted');
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, gradingInstructions: '# Heading' } as ProgrammingExercise);
        fixture.detectChanges();

        // Accessing the computed signal should trigger markdown conversion
        const gradingInstructions = comp.gradingInstructions();
        expect(gradingInstructions).toBe('converted');
        expect(markdownSpy).toHaveBeenCalledWith('# Heading');
    });

    it('should return undefined for empty grading instructions', () => {
        vi.spyOn(markdownService, 'safeHtmlForMarkdown').mockReturnValue('converted');
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, problemStatement: 'problem', gradingInstructions: undefined } as ProgrammingExercise);
        fixture.detectChanges();

        expect(comp.gradingInstructions()).toBeUndefined();
    });
});
