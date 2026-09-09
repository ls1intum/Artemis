import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { UMLDiagramType } from '@tumaet/apollon';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { Component, input, output, signal } from '@angular/core';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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
    let comp: AssessmentInstructionsComponent;
    let fixture: ComponentFixture<AssessmentInstructionsComponent>;
    let markdownService: ArtemisMarkdownService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(ArtemisMarkdownService), { provide: TranslateService, useClass: MockTranslateService }],
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
            exampleSolutionModel: '{"version": "3.0.0", "elements": {}, "relationships": {}}',
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

    describe('grading instruction selection', () => {
        const criteria = [
            {
                id: 1,
                title: 'Documentation',
                structuredGradingInstructions: [
                    { id: 1, credits: 4 },
                    { id: 2, credits: -2 },
                ],
            },
        ] as GradingCriterion[];

        beforeEach(() => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, gradingCriteria: criteria } as ProgrammingExercise);
        });

        it('should not be selectable, and count nothing as applied, without a registered feedback list', () => {
            fixture.detectChanges();

            expect(comp.selectable()).toBe(false);
            expect(comp.appliedInstructionCount()).toEqual({ applied: 0, total: 2 });
        });

        it('should be selectable and count applied instructions once a feedback list is registered', () => {
            const host: GradingInstructionSelectionHost = {
                appliedInstructionIds: signal(new Set([1])),
                appliedInstructionCounts: signal(new Map([[1, 1]])),
                removableInstructionIds: signal(new Set([1])),
                applyInstruction: vi.fn(),
                unapplyOneInstruction: vi.fn(),
                unapplyInstruction: vi.fn(),
            };
            TestBed.inject(GradingInstructionSelectionService).register(host);
            fixture.detectChanges();

            expect(comp.selectable()).toBe(true);
            expect(comp.appliedInstructionCount()).toEqual({ applied: 1, total: 2 });
        });

        it('should stay non-selectable in read-only mode even with a feedback list registered', () => {
            TestBed.inject(GradingInstructionSelectionService).register({
                appliedInstructionIds: signal(new Set()),
                appliedInstructionCounts: signal(new Map()),
                removableInstructionIds: signal(new Set()),
                applyInstruction: vi.fn(),
                unapplyOneInstruction: vi.fn(),
                unapplyInstruction: vi.fn(),
            });
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            expect(comp.selectable()).toBe(false);
        });
    });
});
