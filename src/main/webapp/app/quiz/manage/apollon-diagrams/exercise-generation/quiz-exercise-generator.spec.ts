import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ApollonEditor, ApollonNode, UMLModel, importDiagram } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { computeDropLocation, generateDragAndDropItemForNode, generateDragAndDropQuizExercise } from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
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
    let quizExerciseService: QuizExerciseService;

    const course: Course = { id: 123 } as Course;

    const configureServices = () => {
        quizExerciseService = TestBed.inject(QuizExerciseService);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(TranslateService),
                SessionStorageService,
                LocalStorageService,
                { provide: Router, useClass: MockRouter },
            ],
        }).compileComponents();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('generateDragAndDropExercise for Class Diagram', async () => {
        // TODO: we should mock this differently without require
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const svgRenderer = require('app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        configureServices();
        jest.spyOn(quizExerciseService, 'create').mockImplementation((generatedExercise) => of({ body: generatedExercise } as HttpResponse<QuizExercise>));
        jest.spyOn(svgRenderer, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob());
        jest.spyOn(ApollonEditor, 'exportModelAsSvg').mockResolvedValue({ svg: '<svg></svg>', clip: { width: 100, height: 100, x: 0, y: 0 } } as any);
        const classDiagram: UMLModel = importDiagram({ id: 'diagram', title: 'Diagram', model: testClassDiagram } as unknown as {
            id: string;
            title: string;
            model: UMLModel;
        });
        const nodeEntries = Object.fromEntries(
            (Array.isArray(classDiagram.nodes) ? classDiagram.nodes : Object.values(classDiagram.nodes ?? {})).map((node: any) => [node.id, node]),
        );
        const edgeEntries = Object.fromEntries(
            (Array.isArray(classDiagram.edges) ? classDiagram.edges : Object.values(classDiagram.edges ?? {})).map((edge: any) => [edge.id, edge]),
        );
        (classDiagram as any).nodes = nodeEntries;
        (classDiagram as any).edges = edgeEntries;
        const exerciseTitle = 'GenerateDragAndDropExerciseTest';
        const generatedQuestion = await generateDragAndDropQuizExercise(course, exerciseTitle, classDiagram);
        expect(generatedQuestion).toBeTruthy();
        expect(generatedQuestion.title).toEqual(exerciseTitle);
        expect(generatedQuestion.type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        // create one DragItem for each interactive element
        expect(generatedQuestion.dragItems).toBeDefined();
        expect(generatedQuestion.dragItems!.length).toBeGreaterThan(0);
        // each DragItem needs one DropLocation
        expect(generatedQuestion.dropLocations).toBeDefined();
        expect(generatedQuestion.dropLocations!).toHaveLength(generatedQuestion.dragItems!.length);
        // if there are no similar elements -> amount of correct mappings = interactive elements
        expect(generatedQuestion.correctMappings).toBeDefined();
        expect(generatedQuestion.correctMappings!).toHaveLength(generatedQuestion.dragItems!.length);
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

        const umlModel: UMLModel = importDiagram({ id: 'diagram', title: 'Diagram', model: testClassDiagram } as unknown as {
            id: string;
            title: string;
            model: UMLModel;
        });
        const nodeEntriesForItem = Object.fromEntries((Array.isArray(umlModel.nodes) ? umlModel.nodes : Object.values(umlModel.nodes ?? {})).map((node: any) => [node.id, node]));
        (umlModel as any).nodes = nodeEntriesForItem;

        const umlModelElement = (Object.values(nodeEntriesForItem)[0] as ApollonNode) ?? (Array.isArray(umlModel.nodes) ? (umlModel.nodes as any[])[0] : undefined);

        const fileMap = new Map<string, File>();

        jest.spyOn(ApollonEditor, 'exportModelAsSvg').mockResolvedValue({ svg: '<svg></svg>', clip: { width: 200, height: 200, x: 0, y: 0 } } as any);

        const dragAndDropMapping: DragAndDropMapping = await generateDragAndDropItemForNode(
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
        expect(dragAndDropMapping.dropLocation?.posX).toBeDefined();
        expect(dragAndDropMapping.dropLocation?.posY).toBeDefined();
        expect(dragAndDropMapping.dropLocation?.width).toBeGreaterThan(0);
        expect(dragAndDropMapping.dropLocation?.height).toBeGreaterThan(0);

        jest.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockReset();
    });
});
