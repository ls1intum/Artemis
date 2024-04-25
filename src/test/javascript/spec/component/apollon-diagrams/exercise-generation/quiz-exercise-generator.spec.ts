import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Selection, UMLModel, UMLModelElement, findElement } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import {
    computeDropLocation,
    generateDragAndDropItemForElement,
    generateDragAndDropQuizExercise,
} from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import * as SVGRendererAPI from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import * as testClassDiagram from '../../../util/modeling/test-models/class-diagram.json';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('QuizExercise Generator', () => {
    let quizExerciseService: QuizExerciseService;

    const course: Course = { id: 123 } as Course;

    const configureServices = () => {
        quizExerciseService = TestBed.inject(QuizExerciseService);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [],
            providers: [
                MockProvider(TranslateService),
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
                { provide: Router, useClass: MockRouter },
            ],
            schemas: [],
        }).compileComponents();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('generateDragAndDropExercise for Class Diagram', async () => {
        const svgRenderer = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        configureServices();
        jest.spyOn(quizExerciseService, 'create').mockImplementation((generatedExercise) => of({ body: generatedExercise } as HttpResponse<QuizExercise>));
        jest.spyOn(svgRenderer, 'convertRenderedSVGToPNG').mockReturnValue(new Blob());
        // @ts-ignore
        const classDiagram: UMLModel = testClassDiagram as UMLModel;
        const interactiveElements: Selection = classDiagram.interactive;
        const selectedElements = Object.entries(interactiveElements.elements)
            .filter(([, include]) => include)
            .map(([id]) => id);
        const selectedRelationships = Object.entries(interactiveElements.relationships)
            .filter(([, include]) => include)
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
        jest.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob([]));

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

        jest.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockReset();
    });
});
