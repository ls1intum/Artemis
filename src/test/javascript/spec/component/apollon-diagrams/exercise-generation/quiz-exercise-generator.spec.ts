import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Selection, UMLModel } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { generateDragAndDropQuizExercise } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import * as testClassDiagram from '../../../util/modeling/test-models/class-diagram.json';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

const question1 = { id: 1, type: QuizQuestionType.DRAG_AND_DROP, points: 1 } as QuizQuestion;
const question2 = { id: 2, type: QuizQuestionType.MULTIPLE_CHOICE, points: 2, answerOptions: [], invalid: false, exportQuiz: false, randomizeOrder: true } as QuizQuestion;
const question3 = { id: 3, type: QuizQuestionType.SHORT_ANSWER, points: 3 } as QuizQuestion;
const now = dayjs();

const quizExercise = {
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(2, 'minutes'),
    dueDate: dayjs(now).add(2, 'minutes'),
    quizStarted: true,
} as QuizExercise;

describe('QuizExercise Generator', () => {
    let quizExerciseService: QuizExerciseService;
    let fileUploaderService: FileUploaderService;

    const course: Course = { id: 123 } as Course;

    const configureServices = () => {
        quizExerciseService = TestBed.inject(QuizExerciseService);
        fileUploaderService = TestBed.inject(FileUploaderService);
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
        const examplePath = '/path/to/file';
        jest.spyOn(fileUploaderService, 'uploadFile').mockReturnValue(Promise.resolve({ path: examplePath }));
        jest.spyOn(quizExerciseService, 'create').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
        jest.spyOn(svgRenderer, 'convertRenderedSVGToPNG').mockReturnValue(new Blob());
        // @ts-ignore
        const classDiagram: UMLModel = testClassDiagram as UMLModel;
        const interactiveElements: Selection = classDiagram.interactive;
        const exerciseTitle = 'GenerateDragAndDropExerciseTest';
        const generatedExercise = await generateDragAndDropQuizExercise(course, exerciseTitle, classDiagram, fileUploaderService, quizExerciseService);
        expect(generatedExercise).toBeTruthy();
        expect(generatedExercise.title).toEqual(exerciseTitle);
        expect(generatedExercise.quizQuestions![0].type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        const dragAndDropQuestion = generatedExercise.quizQuestions![0] as DragAndDropQuestion;
        // create one DragItem for each interactive element
        expect(dragAndDropQuestion.dragItems).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
        // each DragItem needs one DropLocation
        expect(dragAndDropQuestion.dropLocations).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
        // if there are no similar elements -> amount of correct mappings = interactive elements
        expect(dragAndDropQuestion.correctMappings).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
    });
});
