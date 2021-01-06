import { UMLModel, Selection } from '@ls1intum/apollon';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Course } from 'app/entities/course.model';
import { TestBed } from '@angular/core/testing';
import * as sinon from 'sinon';
import { generateDragAndDropQuizExercise } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import * as testClassDiagram from '../../../util/modeling/test-models/class-diagram.json';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Router } from '@angular/router';
import { Text } from '@ls1intum/apollon/lib/utils/svg/text';
import { of } from 'rxjs';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('QuizExercise Generator', () => {
    let quizExerciseService: QuizExerciseService;
    let fileUploaderService: FileUploaderService;
    const sandbox = sinon.createSandbox();

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

    afterEach(function () {
        sandbox.restore();
    });

    it('generateDragAndDropExercise for Class Diagram', async (done) => {
        const svgRenderer = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        configureServices();
        const examplePath = '/path/to/file';
        sandbox.stub(fileUploaderService, 'uploadFile').resolves({ path: examplePath });
        sandbox.stub(quizExerciseService, 'create').returns(of());
        sandbox.stub(svgRenderer, 'convertRenderedSVGToPNG').resolves(new Blob());
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
        done();
    });
});
