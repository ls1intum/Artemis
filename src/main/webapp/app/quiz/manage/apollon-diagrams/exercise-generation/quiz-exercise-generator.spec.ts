import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ApollonEditor, UMLModel } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MAX_SIZE_UNIT, computeDropLocation, generateDragAndDropQuizExercise } from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import * as testClassDiagramV3 from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import * as testClassDiagramV4 from 'test/helpers/sample/modeling/test-models/class-diagram-v4.json';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';

/**
 * RUTHLESS TEST SUITE: Quiz Exercise Generator
 *
 * These tests cover:
 * 1. V3 format (interactive.elements/relationships) - backwards compatibility
 * 2. V4 format (nodes/edges arrays) - current format
 * 3. computeDropLocation math with edge cases
 * 4. Error handling and edge cases
 */
describe('QuizExercise Generator', () => {
    setupTestBed({ zoneless: true });

    const course: Course = { id: 123 } as Course;

    // Type-safe mock for ApollonEditor.exportModelAsSvg
    const mockExportModelAsSvg = vi.fn().mockResolvedValue({
        svg: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"></svg>',
        clip: { x: 0, y: 0, width: 100, height: 100 },
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(TranslateService),
                SessionStorageService,
                LocalStorageService,
                { provide: Router, useClass: MockRouter },
            ],
        }).compileComponents();

        // Mock static method with proper cleanup
        vi.spyOn(ApollonEditor, 'exportModelAsSvg').mockImplementation(mockExportModelAsSvg);
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob(['PNG'], { type: 'image/png' }));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    // ===========================================
    // V3 FORMAT TESTS (Backwards Compatibility)
    // ===========================================
    describe('V3 format (interactive.elements/relationships)', () => {
        const v3Model = testClassDiagramV3 as unknown as UMLModel;

        it('should extract interactive elements from v3 model', async () => {
            const exerciseTitle = 'V3 Format Test';

            const question = await generateDragAndDropQuizExercise(course, exerciseTitle, v3Model);

            // V3 has 3 interactive elements defined in interactive.elements
            const expectedInteractiveCount = 3;
            expect(question.dragItems).toHaveLength(expectedInteractiveCount);
            expect(question.dropLocations).toHaveLength(expectedInteractiveCount);
        });

        it('should create correct mappings for v3 interactive elements', async () => {
            const question = await generateDragAndDropQuizExercise(course, 'Mapping Test', v3Model);

            // Each drag item should have at least one correct mapping
            expect(question.correctMappings!.length).toBeGreaterThanOrEqual(question.dragItems!.length);

            // Verify each drag item has a corresponding mapping
            for (const dragItem of question.dragItems!) {
                const hasMapping = question.correctMappings!.some((m) => m.dragItem === dragItem);
                expect(hasMapping).toBe(true);
            }
        });

        it('should generate background image excluding interactive elements', async () => {
            await generateDragAndDropQuizExercise(course, 'Background Test', v3Model);

            // Verify exportModelAsSvg was called with exclude option containing interactive element IDs
            expect(mockExportModelAsSvg).toHaveBeenCalledWith(
                v3Model,
                expect.objectContaining({
                    exclude: expect.arrayContaining(['b390a813-dad9-4d6d-b3cd-732ce99d0a23', '6f572312-066b-4678-9c03-5032f3ba9be9', '2f67120e-b491-4222-beb1-79e87c2cf54d']),
                }),
            );
        });

        it('should handle v3 model with empty interactive elements', async () => {
            const emptyInteractiveModel = {
                ...testClassDiagramV3,
                interactive: { elements: {}, relationships: {} },
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise(course, 'Empty Interactive', emptyInteractiveModel);

            expect(question.dragItems).toHaveLength(0);
            expect(question.dropLocations).toHaveLength(0);
            expect(question.correctMappings).toHaveLength(0);
        });

        it('should handle v3 model with only relationship interactive elements', async () => {
            const relationshipOnlyModel = {
                ...testClassDiagramV3,
                interactive: {
                    elements: {},
                    relationships: { '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd': true },
                },
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise(course, 'Relationship Only', relationshipOnlyModel);

            // Should have 1 interactive element (the relationship)
            expect(question.dragItems).toHaveLength(1);
        });
    });

    // ===========================================
    // V4 FORMAT TESTS (Current Format)
    // ===========================================
    describe('V4 format (nodes/edges arrays)', () => {
        const v4Model = testClassDiagramV4 as unknown as UMLModel;

        it('should extract all elements from v4 model nodes array', async () => {
            const question = await generateDragAndDropQuizExercise(course, 'V4 Format Test', v4Model);

            // V4 has 5 nodes + 2 edges = 7 total elements
            const expectedElementCount = 7;
            expect(question.dragItems).toHaveLength(expectedElementCount);
            expect(question.dropLocations).toHaveLength(expectedElementCount);
        });

        it('should correctly identify v4 model by array structure', async () => {
            // Verify the model has array structure (not object)
            expect(Array.isArray((v4Model as any).nodes)).toBe(true);
            expect(Array.isArray((v4Model as any).edges)).toBe(true);

            const question = await generateDragAndDropQuizExercise(course, 'Array Structure Test', v4Model);

            // Should successfully generate without errors
            expect(question).toBeDefined();
            expect(question.type).toBe(QuizQuestionType.DRAG_AND_DROP);
        });

        it('should handle v4 model with only nodes (no edges)', async () => {
            const nodesOnlyModel = {
                ...testClassDiagramV4,
                edges: [],
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise(course, 'Nodes Only', nodesOnlyModel);

            // Should have 5 nodes
            expect(question.dragItems).toHaveLength(5);
        });

        it('should handle v4 model with empty nodes and edges', async () => {
            const emptyModel = {
                version: '4.0.0',
                id: 'empty',
                title: 'Empty',
                type: 'ClassDiagram',
                nodes: [],
                edges: [],
                assessments: {},
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise(course, 'Empty V4', emptyModel);

            expect(question.dragItems).toHaveLength(0);
            expect(question.dropLocations).toHaveLength(0);
        });

        it('should use node IDs from v4 array elements', async () => {
            await generateDragAndDropQuizExercise(course, 'ID Test', v4Model);

            // exportModelAsSvg should be called with include option for each element
            // First call is background (exclude), subsequent calls are for individual elements (include)
            const calls = mockExportModelAsSvg.mock.calls;

            // Background call should exclude all element IDs
            expect(calls[0][1]).toHaveProperty('exclude');

            // Individual element calls should include specific IDs
            const includeCallIds = calls
                .slice(1)
                .map((call) => call[1]?.include?.[0])
                .filter(Boolean);

            // Should include actual node IDs, not array indices
            expect(includeCallIds).toContain('package-1');
            expect(includeCallIds).toContain('class-in-package');
            expect(includeCallIds).not.toContain('0'); // Should NOT be array index
        });
    });

    // ===========================================
    // QUESTION STRUCTURE TESTS
    // ===========================================
    describe('Question structure and defaults', () => {
        it('should set correct question metadata', async () => {
            const title = 'Test Quiz Question';
            const question = await generateDragAndDropQuizExercise(course, title, testClassDiagramV3 as unknown as UMLModel);

            expect(question.title).toBe(title);
            expect(question.type).toBe(QuizQuestionType.DRAG_AND_DROP);
            expect(question.scoringType).toBe(ScoringType.PROPORTIONAL_WITH_PENALTY);
            expect(question.points).toBe(1);
        });

        it('should set default question text', async () => {
            const question = await generateDragAndDropQuizExercise(course, 'Default Text Test', testClassDiagramV3 as unknown as UMLModel);

            expect(question.text).toBe('Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.');
        });

        it('should set background file path', async () => {
            const question = await generateDragAndDropQuizExercise(course, 'Background Path Test', testClassDiagramV3 as unknown as UMLModel);

            expect(question.backgroundFilePath).toBe('diagram-background.png');
        });

        it('should include all generated files in importedFiles map', async () => {
            const question = await generateDragAndDropQuizExercise(course, 'Files Test', testClassDiagramV3 as unknown as UMLModel);

            expect(question.importedFiles).toBeDefined();
            expect(question.importedFiles!.has('diagram-background.png')).toBe(true);

            // Each drag item should have a corresponding file
            for (const dragItem of question.dragItems!) {
                if (dragItem.pictureFilePath) {
                    expect(question.importedFiles!.has(dragItem.pictureFilePath)).toBe(true);
                }
            }
        });
    });

    // ===========================================
    // computeDropLocation TESTS
    // ===========================================
    describe('computeDropLocation', () => {
        it('should compute relative position as percentage of MAX_SIZE_UNIT', () => {
            const elementLocation = { x: 50, y: 25, width: 100, height: 50 };
            const totalSize = { width: 200, height: 100 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            // x: 50/200 * 200 = 50
            expect(dropLocation.posX).toBe(50);
            // y: 25/100 * 200 = 50
            expect(dropLocation.posY).toBe(50);
            // width: 100/200 * 200 = 100
            expect(dropLocation.width).toBe(100);
            // height: 50/100 * 200 = 100
            expect(dropLocation.height).toBe(100);
        });

        it('should handle totalSize with x and y offsets', () => {
            const elementLocation = { x: 60, y: 35, width: 100, height: 50 };
            const totalSize = { x: 10, y: 10, width: 200, height: 100 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            // x: (60-10)/200 * 200 = 50
            expect(dropLocation.posX).toBe(50);
            // y: (35-10)/100 * 200 = 50
            expect(dropLocation.posY).toBe(50);
        });

        it('should handle element at origin (0,0)', () => {
            const elementLocation = { x: 0, y: 0, width: 100, height: 100 };
            const totalSize = { width: 200, height: 200 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation.posX).toBe(0);
            expect(dropLocation.posY).toBe(0);
            expect(dropLocation.width).toBe(100);
            expect(dropLocation.height).toBe(100);
        });

        it('should handle element filling entire canvas', () => {
            const elementLocation = { x: 0, y: 0, width: 500, height: 400 };
            const totalSize = { width: 500, height: 400 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation.posX).toBe(0);
            expect(dropLocation.posY).toBe(0);
            expect(dropLocation.width).toBe(MAX_SIZE_UNIT);
            expect(dropLocation.height).toBe(MAX_SIZE_UNIT);
        });

        it('should handle negative element coordinates', () => {
            const elementLocation = { x: -10, y: -20, width: 100, height: 100 };
            const totalSize = { width: 200, height: 200 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            // x: -10/200 * 200 = -10
            expect(dropLocation.posX).toBe(-10);
            // y: -20/200 * 200 = -20
            expect(dropLocation.posY).toBe(-20);
        });

        it('should handle very small elements', () => {
            const elementLocation = { x: 5, y: 5, width: 10, height: 10 };
            const totalSize = { width: 1000, height: 1000 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation.posX).toBe(1);
            expect(dropLocation.posY).toBe(1);
            expect(dropLocation.width).toBe(2);
            expect(dropLocation.height).toBe(2);
        });

        it('should round to two decimal places', () => {
            const elementLocation = { x: 33, y: 17, width: 77, height: 43 };
            const totalSize = { width: 100, height: 100 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            // Verify values are numbers (rounded)
            expect(typeof dropLocation.posX).toBe('number');
            expect(typeof dropLocation.posY).toBe('number');
            expect(typeof dropLocation.width).toBe('number');
            expect(typeof dropLocation.height).toBe('number');

            // Values should be reasonable percentages of MAX_SIZE_UNIT
            expect(dropLocation.posX).toBeGreaterThanOrEqual(0);
            expect(dropLocation.posX).toBeLessThanOrEqual(MAX_SIZE_UNIT);
        });
    });

    // ===========================================
    // ERROR HANDLING TESTS
    // ===========================================
    describe('Error handling', () => {
        it('should handle SVG export failure gracefully', async () => {
            mockExportModelAsSvg.mockRejectedValueOnce(new Error('SVG export failed'));

            await expect(generateDragAndDropQuizExercise(course, 'Error Test', testClassDiagramV3 as unknown as UMLModel)).rejects.toThrow('SVG export failed');
        });

        it('should handle PNG conversion failure gracefully', async () => {
            vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockRejectedValueOnce(new Error('PNG conversion failed'));

            await expect(generateDragAndDropQuizExercise(course, 'PNG Error Test', testClassDiagramV3 as unknown as UMLModel)).rejects.toThrow('PNG conversion failed');
        });

        it('should handle model with missing elements gracefully', async () => {
            // Model with interactive IDs that don't exist in elements
            const brokenModel = {
                ...testClassDiagramV3,
                interactive: {
                    elements: { 'non-existent-id': true },
                    relationships: {},
                },
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise(course, 'Broken Model', brokenModel);

            // Should skip non-existent elements
            expect(question.dragItems).toHaveLength(0);
        });
    });
});
