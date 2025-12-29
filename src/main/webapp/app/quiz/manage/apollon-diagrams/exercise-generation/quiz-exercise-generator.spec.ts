import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Selection, UMLModel, UMLModelElement, findElement } from '@ls1intum/apollon';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import {
    computeDropLocation,
    generateDragAndDropItemForElement,
    generateDragAndDropQuizExercise,
} from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import * as testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';

describe('QuizExercise Generator', () => {
    setupTestBed({ zoneless: true });

    let quizExerciseService: QuizExerciseService;

    const course: Course = { id: 123 } as Course;

    const configureServices = () => {
        quizExerciseService = TestBed.inject(QuizExerciseService);
    };

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

        configureServices();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('generateDragAndDropExercise for Class Diagram', async () => {
        vi.spyOn(quizExerciseService, 'create').mockImplementation((generatedExercise) => of({ body: generatedExercise } as HttpResponse<QuizExercise>));
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob());
        // @ts-ignore
        const classDiagram: UMLModel = testClassDiagram as UMLModel;
        const interactiveElements: Selection = classDiagram.interactive;
        const selectedElements = Object.entries(interactiveElements.elements)
            .filter(([include]) => include)
            .map(([id]) => id);
        const selectedRelationships = Object.entries(interactiveElements.relationships)
            .filter(([include]) => include)
            .map(([id]) => id);
        const exerciseTitle = 'GenerateDragAndDropExerciseTest';
        const generatedQuestion = await generateDragAndDropQuizExercise(course, exerciseTitle, classDiagram);
        expect(generatedQuestion).toBeTruthy();
        expect(generatedQuestion.title).toEqual(exerciseTitle);
        expect(generatedQuestion.type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        // create one DragItem for each interactive element
        expect(generatedQuestion.dragItems).toHaveLength(selectedElements.length + selectedRelationships.length);
        // each DragItem needs one DropLocation
        expect(generatedQuestion.dropLocations).toHaveLength(selectedElements.length + selectedRelationships.length);
        // if there are no similar elements -> amount of correct mappings = interactive elements
        expect(generatedQuestion.correctMappings).toHaveLength(selectedElements.length + selectedRelationships.length);
    });

    it('computeDropLocation with totalSize x and y coordinates', async () => {
        const elementLocation = { x: 20, y: 20, width: 400, height: 500 };
        const totalSize = { x: 10, y: 10, width: 400, height: 500 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(5);
        expect(dropLocation.posY).toBe(4);
        expect(dropLocation.width).toBe(200);
        expect(dropLocation.height).toBe(200);
    });

    it('computeDropLocation without totalSize x and y coordinates', async () => {
        const elementLocation = { x: 10, y: 20, width: 400, height: 500 };
        const totalSize = { width: 400, height: 500 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(5);
        expect(dropLocation.posY).toBe(8);
        expect(dropLocation.width).toBe(200);
        expect(dropLocation.height).toBe(200);
    });

    it('computeDropLocation with negative element location coordinates', async () => {
        const elementLocation = { x: -10, y: -10, width: 400, height: 500 };
        const totalSize = { x: 10, y: 10, width: 400, height: 500 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(-10);
        expect(dropLocation.posY).toBe(-8);
        expect(dropLocation.width).toBe(200);
        expect(dropLocation.height).toBe(200);
    });

    it('generateDragAndDropItemForElement', async () => {
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob([]));

        const umlModel: UMLModel = testClassDiagram as unknown as UMLModel;

        const umlModelElement: UMLModelElement = findElement(umlModel, 'fea23cbc-8df0-4dcc-9d7a-eb86fbb2ce9d')!;

        const fileMap = new Map<string, File>();

        const dragAndDropMapping: DragAndDropMapping = await generateDragAndDropItemForElement(
            umlModelElement,
            umlModel,
            {
                height: 400,
                width: 400,
            },
            fileMap,
        );

        const expectedFileName = `element-${umlModelElement.id}.png`;

        expect(fileMap.get(expectedFileName)).toBeDefined();
        expect(dragAndDropMapping.dragItem?.pictureFilePath).toEqual(expectedFileName);
        expect(dragAndDropMapping.dropLocation?.posX).toBe(292.5);
        expect(dragAndDropMapping.dropLocation?.posY).toBe(207.5);
        expect(dragAndDropMapping.dropLocation?.width).toBe(114.5);
        expect(dragAndDropMapping.dropLocation?.height).toBe(30);

        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockReset();
    });

    it('computeDropLocation at origin', () => {
        const elementLocation = { x: 0, y: 0, width: 100, height: 100 };
        const totalSize = { width: 200, height: 200 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(0);
        expect(dropLocation.posY).toBe(0);
        expect(dropLocation.width).toBe(100);
        expect(dropLocation.height).toBe(100);
    });

    it('computeDropLocation for small element', () => {
        const elementLocation = { x: 5, y: 5, width: 10, height: 10 };
        const totalSize = { width: 1000, height: 1000 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(1);
        expect(dropLocation.posY).toBe(1);
        expect(dropLocation.width).toBe(2);
        expect(dropLocation.height).toBe(2);
    });

    it('computeDropLocation for element filling entire canvas', () => {
        const elementLocation = { x: 0, y: 0, width: 500, height: 400 };
        const totalSize = { width: 500, height: 400 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        expect(dropLocation.posX).toBe(0);
        expect(dropLocation.posY).toBe(0);
        expect(dropLocation.width).toBe(200);
        expect(dropLocation.height).toBe(200);
    });

    it('computeDropLocation rounds to two decimal places', () => {
        const elementLocation = { x: 33, y: 17, width: 77, height: 43 };
        const totalSize = { width: 100, height: 100 };

        const dropLocation = computeDropLocation(elementLocation, totalSize);

        // Check that values are rounded appropriately
        expect(dropLocation.posX).toBe(66);
        expect(dropLocation.posY).toBe(34);
        expect(dropLocation.width).toBe(154);
        expect(dropLocation.height).toBe(86);
    });

    it('generateDragAndDropExercise handles empty interactive elements', async () => {
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob());

        // Create a model with no interactive elements
        const emptyModel: UMLModel = {
            ...testClassDiagram,
            interactive: {
                elements: {},
                relationships: {},
            },
        } as unknown as UMLModel;

        const exerciseTitle = 'EmptyInteractiveTest';
        const generatedQuestion = await generateDragAndDropQuizExercise(course, exerciseTitle, emptyModel);

        expect(generatedQuestion).toBeTruthy();
        expect(generatedQuestion.title).toEqual(exerciseTitle);
        expect(generatedQuestion.dragItems).toHaveLength(0);
        expect(generatedQuestion.dropLocations).toHaveLength(0);
        expect(generatedQuestion.correctMappings).toHaveLength(0);
    });

    it('generateDragAndDropExercise sets default text and scoring', async () => {
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob());

        const classDiagram: UMLModel = testClassDiagram as unknown as UMLModel;
        const exerciseTitle = 'DefaultValuesTest';

        const generatedQuestion = await generateDragAndDropQuizExercise(course, exerciseTitle, classDiagram);

        expect(generatedQuestion.text).toBe('Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.');
        expect(generatedQuestion.points).toBe(1);
        expect(generatedQuestion.backgroundFilePath).toBe('diagram-background.png');
        expect(generatedQuestion.importedFiles).toBeDefined();
        expect(generatedQuestion.importedFiles!.has('diagram-background.png')).toBeTrue();
    });
});
