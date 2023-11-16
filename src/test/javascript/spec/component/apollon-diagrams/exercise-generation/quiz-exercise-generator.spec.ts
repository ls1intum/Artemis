import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Selection, UMLModel } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { generateDragAndDropQuizExercise } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
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
        const exerciseTitle = 'GenerateDragAndDropExerciseTest';
        const generatedQuestion = await generateDragAndDropQuizExercise(course, exerciseTitle, classDiagram);
        expect(generatedQuestion).toBeTruthy();
        expect(generatedQuestion.title).toEqual(exerciseTitle);
        expect(generatedQuestion.type).toEqual(QuizQuestionType.DRAG_AND_DROP);
        // create one DragItem for each interactive element
        expect(generatedQuestion.dragItems).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
        // each DragItem needs one DropLocation
        expect(generatedQuestion.dropLocations).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
        // if there are no similar elements -> amount of correct mappings = interactive elements
        expect(generatedQuestion.correctMappings).toHaveLength(interactiveElements.elements.length + interactiveElements.relationships.length);
    });
});
