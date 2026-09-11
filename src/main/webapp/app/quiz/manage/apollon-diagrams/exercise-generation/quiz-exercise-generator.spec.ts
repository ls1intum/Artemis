import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ApollonEditor, UMLModel } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MAX_SIZE_UNIT, computeDropLocation, generateDragAndDropQuizExercise } from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import testClassDiagramV3 from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import testClassDiagramV4 from 'test/helpers/sample/modeling/test-models/class-diagram-v4.json';

function setupCanvasAndImageMocks() {
    const createMockCanvas = () => {
        const mockContext = {
            drawImage: vi.fn(),
            fillStyle: '',
            fillRect: vi.fn(),
            scale: vi.fn(),
            globalCompositeOperation: 'source-over',
        };

        return {
            style: { width: '', height: '' },
            getContext: vi.fn().mockReturnValue(mockContext),
            toBlob: vi.fn((callback: (blob: Blob | null) => void) => callback(new Blob(['PNG'], { type: 'image/png' }))),
            width: 0,
            height: 0,
        } as unknown as HTMLCanvasElement;
    };

    // eslint-disable-next-line @typescript-eslint/no-deprecated
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
        if (tagName === 'canvas') {
            return createMockCanvas();
        }
        return originalCreateElement(tagName);
    });

    const originalImage = globalThis.Image;
    class MockImage {
        width = 100;
        height = 100;
        private _src = '';
        onload: (() => void) | null = null;
        onerror: ((error: Event | string) => void) | null = null;

        get src() {
            return this._src;
        }

        set src(value: string) {
            this._src = value;
            queueMicrotask(() => this.onload?.());
        }
    }

    vi.stubGlobal('Image', MockImage as any);
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-url');
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    return {
        cleanup: () => {
            createElementSpy.mockRestore();
            createObjectURLSpy.mockRestore();
            revokeObjectURLSpy.mockRestore();
            vi.unstubAllGlobals();
            globalThis.Image = originalImage;
        },
    };
}

describe('QuizExercise Generator', () => {
    let cleanupCanvasAndImageMocks: (() => void) | undefined;
    const defaultExportModelAsSvgResult = {
        svg: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"></svg>',
        clip: { x: 0, y: 0, width: 100, height: 100 },
    };

    const mockExportModelAsSvg = vi.fn().mockResolvedValue(defaultExportModelAsSvgResult);

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

        mockExportModelAsSvg.mockReset();
        mockExportModelAsSvg.mockResolvedValue(defaultExportModelAsSvgResult);

        vi.spyOn(ApollonEditor, 'exportModelAsSvg').mockImplementation(mockExportModelAsSvg);
        vi.spyOn(SVGRendererAPI, 'cropRenderedSVGToElement').mockReturnValue(defaultExportModelAsSvgResult);
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob(['PNG'], { type: 'image/png' }));
        cleanupCanvasAndImageMocks = setupCanvasAndImageMocks().cleanup;
    });

    afterEach(() => {
        cleanupCanvasAndImageMocks?.();
        cleanupCanvasAndImageMocks = undefined;
        vi.restoreAllMocks();
    });

    describe('V3 format (interactive.elements/relationships)', () => {
        const v3Model = testClassDiagramV3 as unknown as UMLModel;

        it('should extract interactive elements from v3 model', async () => {
            const exerciseTitle = 'V3 Format Test';

            const question = await generateDragAndDropQuizExercise(exerciseTitle, v3Model);

            expect(question.dragItems).toHaveLength(3);
            expect(question.dropLocations).toHaveLength(3);
            expect(Array.isArray(mockExportModelAsSvg.mock.calls[0][0].nodes)).toBe(true);
        });

        it('should create correct mappings for v3 interactive elements', async () => {
            const question = await generateDragAndDropQuizExercise('Mapping Test', v3Model);

            expect(question.correctMappings!.length).toBeGreaterThanOrEqual(question.dragItems!.length);

            for (const dragItem of question.dragItems!) {
                const hasMapping = question.correctMappings!.some((m) => m.dragItem === dragItem);
                expect(hasMapping).toBe(true);
            }
        });

        it('should generate background image excluding interactive elements', async () => {
            await generateDragAndDropQuizExercise('Background Test', v3Model);

            const calls = mockExportModelAsSvg.mock.calls;
            const topLevelInteractiveIds = ['2f67120e-b491-4222-beb1-79e87c2cf54d'];

            expect(calls[calls.length - 1][1]).toEqual(
                expect.objectContaining({
                    exclude: expect.arrayContaining(topLevelInteractiveIds),
                    keepOriginalSize: true,
                    svgMode: 'compat',
                }),
            );
        });

        it('should preserve an explicitly empty interactive selection', async () => {
            const emptyInteractiveModel = {
                ...testClassDiagramV3,
                interactive: { elements: {}, relationships: {} },
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise('Empty Interactive', emptyInteractiveModel);

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

            const question = await generateDragAndDropQuizExercise('Relationship Only', relationshipOnlyModel);

            expect(question.dragItems).toHaveLength(1);
        });
    });

    describe('V4 format (nodes/edges arrays)', () => {
        const v4Model = testClassDiagramV4 as unknown as UMLModel;

        it('should extract all elements from v4 model nodes array', async () => {
            const question = await generateDragAndDropQuizExercise('V4 Format Test', v4Model);

            expect(question.dragItems).toHaveLength(15);
            expect(question.dropLocations).toHaveLength(15);
        });

        it('should isolate the source model from export normalization', async () => {
            const serializedModel = JSON.stringify(v4Model);

            await generateDragAndDropQuizExercise('Immutable Source Test', v4Model);

            expect(JSON.stringify(v4Model)).toBe(serializedModel);
            expect(mockExportModelAsSvg.mock.calls[0][0]).not.toBe(v4Model);
        });

        it('should handle v4 model with only nodes (no edges)', async () => {
            const nodesOnlyModel = {
                ...testClassDiagramV4,
                edges: [],
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise('Nodes Only', nodesOnlyModel);

            expect(question.dragItems).toHaveLength(13);
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

            const question = await generateDragAndDropQuizExercise('Empty V4', emptyModel);

            expect(question.dragItems).toHaveLength(0);
            expect(question.dropLocations).toHaveLength(0);
        });

        it('should use node IDs from v4 array elements', async () => {
            await generateDragAndDropQuizExercise('ID Test', v4Model);

            const calls = mockExportModelAsSvg.mock.calls;

            expect(calls[0][1]).toEqual(expect.objectContaining({ keepOriginalSize: true, svgMode: 'compat' }));

            const includeCallIds = calls
                .slice(1)
                .map((call) => call[1]?.include?.[0])
                .filter(Boolean);

            expect(includeCallIds).toContain('package-1');
            expect(includeCallIds).toContain('class-in-package');
            expect(includeCallIds).not.toContain('0');

            expect(calls[calls.length - 1][1]).toEqual(
                expect.objectContaining({
                    exclude: expect.arrayContaining([...v4Model.nodes.map(({ id }) => id), ...v4Model.edges.map(({ id }) => id)]),
                    keepOriginalSize: true,
                    svgMode: 'compat',
                }),
            );
        });
    });

    describe('Question structure and defaults', () => {
        it('creates a complete drag-and-drop question contract', async () => {
            const title = 'Test Quiz Question';
            const question = await generateDragAndDropQuizExercise(title, testClassDiagramV3 as unknown as UMLModel);

            expect(question.title).toBe(title);
            expect(question.type).toBe(QuizQuestionType.DRAG_AND_DROP);
            expect(question.scoringType).toBe(ScoringType.PROPORTIONAL_WITH_PENALTY);
            expect(question.points).toBe(1);
            expect(question.text).toBe('Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.');
            expect(question.backgroundFilePath).toBe('diagram-background.png');
            expect(question.importedFiles).toBeDefined();
            expect(question.importedFiles!.has('diagram-background.png')).toBe(true);

            for (const dragItem of question.dragItems!) {
                if (dragItem.pictureFilePath) {
                    expect(question.importedFiles!.has(dragItem.pictureFilePath)).toBe(true);
                }
            }
        });
    });

    describe('computeDropLocation', () => {
        it('should compute relative position as percentage of MAX_SIZE_UNIT', () => {
            const elementLocation = { x: 50, y: 25, width: 100, height: 50 };
            const totalSize = { width: 200, height: 100 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation.posX).toBe(50);
            expect(dropLocation.posY).toBe(50);
            expect(dropLocation.width).toBe(100);
            expect(dropLocation.height).toBe(100);
        });

        it('should handle totalSize with x and y offsets', () => {
            const elementLocation = { x: 60, y: 35, width: 100, height: 50 };
            const totalSize = { x: 10, y: 10, width: 200, height: 100 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation.posX).toBe(50);
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

            expect(dropLocation.posX).toBe(-10);
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
            const elementLocation = { x: 1, y: 2, width: 1, height: 2 };
            const totalSize = { width: 3, height: 3 };

            const dropLocation = computeDropLocation(elementLocation, totalSize);

            expect(dropLocation).toMatchObject({ posX: 66.67, posY: 133.33, width: 66.67, height: 133.33 });
        });
    });

    describe('Error handling', () => {
        it('should handle SVG export failure gracefully', async () => {
            mockExportModelAsSvg.mockRejectedValueOnce(new Error('SVG export failed'));

            await expect(generateDragAndDropQuizExercise('Error Test', testClassDiagramV3 as unknown as UMLModel)).rejects.toThrow('SVG export failed');
        });

        it('should handle PNG conversion failure gracefully', async () => {
            vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockRejectedValueOnce(new Error('PNG conversion failed'));

            await expect(generateDragAndDropQuizExercise('PNG Error Test', testClassDiagramV3 as unknown as UMLModel)).rejects.toThrow('PNG conversion failed');
        });

        it('should handle model with missing elements gracefully', async () => {
            const brokenModel = {
                ...testClassDiagramV3,
                interactive: {
                    elements: { 'non-existent-id': true },
                    relationships: {},
                },
            } as unknown as UMLModel;

            const question = await generateDragAndDropQuizExercise('Broken Model', brokenModel);

            expect(question.dragItems).toHaveLength(0);
        });
    });
});
