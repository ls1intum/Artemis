import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { QuizQuestionListEditExistingComponent, State } from 'app/exercises/quiz/manage/quiz-question-list-edit-existing.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { FormsModule } from '@angular/forms';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ChangeDetectorRef, EventEmitter } from '@angular/core';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { FileService } from 'app/shared/http/file.service';

const createValidMCQuestion = () => {
    const question = new MultipleChoiceQuestion();
    question.title = 'test';
    const answerOption1 = new AnswerOption();
    answerOption1.text = 'right answer';
    answerOption1.explanation = 'right explanation';
    answerOption1.isCorrect = true;
    const answerOption2 = new AnswerOption();
    answerOption2.text = 'wrong answer';
    answerOption2.explanation = 'wrong explanation';
    answerOption2.hint = 'wrong hint';
    answerOption2.isCorrect = false;
    question.answerOptions = [answerOption1, answerOption2];
    question.points = 10;
    return { question, answerOption1, answerOption2 };
};

const createValidDnDQuestion = () => {
    const question = new DragAndDropQuestion();
    question.title = 'test';
    const dragItem1 = new DragItem();
    dragItem1.text = 'dragItem 1';
    dragItem1.pictureFilePath = 'test';
    const dragItem2 = new DragItem();
    dragItem2.text = 'dragItem 1';
    question.dragItems = [dragItem1, dragItem2];
    const dropLocation = new DropLocation();
    dropLocation.posX = 50;
    dropLocation.posY = 60;
    dropLocation.width = 70;
    dropLocation.height = 80;
    question.dropLocations = [dropLocation];
    const correctDragAndDropMapping = new DragAndDropMapping(dragItem1, dropLocation);
    question.correctMappings = [correctDragAndDropMapping];
    question.points = 10;
    return { question, dragItem1, dragItem2, dropLocation, correctDragAndDropMapping };
};

const createValidSAQuestion = () => {
    const question = new ShortAnswerQuestion();
    question.title = 'test';
    const shortAnswerSolution1 = new ShortAnswerSolution();
    shortAnswerSolution1.text = 'solution 1';
    const shortAnswerSolution2 = new ShortAnswerSolution();
    shortAnswerSolution2.text = 'solution 2';
    question.solutions = [shortAnswerSolution1, shortAnswerSolution2];
    const spot1 = new ShortAnswerSpot();
    spot1.question = question;
    spot1.spotNr = 1;
    spot1.width = 50;
    const spot2 = new ShortAnswerSpot();
    spot2.question = question;
    spot2.spotNr = 2;
    spot2.width = 70;
    question.spots = [spot1, spot2];
    const shortAnswerMapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
    const shortAnswerMapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
    question.correctMappings = [shortAnswerMapping1, shortAnswerMapping2];
    question.points = 10;
    return { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 };
};

describe('QuizQuestionListEditExistingComponent', () => {
    let fixture: ComponentFixture<QuizQuestionListEditExistingComponent>;
    let component: QuizQuestionListEditExistingComponent;
    let courseService: CourseManagementService;
    let examService: ExamManagementService;
    let quizExerciseService: QuizExerciseService;
    let fileService: FileService;
    let changeDetector: ChangeDetectorRef;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CommonModule, ArtemisTestModule, HttpClientTestingModule, FormsModule],
            declarations: [QuizQuestionListEditExistingComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbModal), MockProvider(ChangeDetectorRef)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizQuestionListEditExistingComponent);
                examService = fixture.debugElement.injector.get(ExamManagementService);
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
                fileService = TestBed.inject(FileService);
                changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
                modalService = fixture.debugElement.injector.get(NgbModal);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    describe('ngOnChanges', () => {
        it('should load exams when show is true', () => {
            const exam = new Exam();
            const course = new Course();
            const getAllCoursesWithQuizExercisesSpy = jest
                .spyOn(courseService, 'getAllCoursesWithQuizExercises')
                .mockReturnValue(of(new HttpResponse<Course[]>({ body: [course] })));
            const findAllExamsAccessibleToUserSpy = jest.spyOn(examService, 'findAllExamsAccessibleToUser').mockReturnValue(of(new HttpResponse<Exam[]>({ body: [exam] })));
            component.show = true;
            component.ngOnChanges();
            expect(getAllCoursesWithQuizExercisesSpy).toHaveBeenCalledOnce();
            expect(findAllExamsAccessibleToUserSpy).toHaveBeenCalledOnce();
            expect(component.courses).toBeArrayOfSize(1);
            expect(component.courses[0]).toEqual(course);
            expect(component.exams).toBeArrayOfSize(1);
            expect(component.exams[0]).toEqual(exam);
        });

        it('should not load exams when show is false', () => {
            const getAllCoursesWithQuizExercisesSpy = jest.spyOn(courseService, 'getAllCoursesWithQuizExercises');
            const findAllExamsAccessibleToUserSpy = jest.spyOn(examService, 'findAllExamsAccessibleToUser');
            component.show = false;
            component.exams = [];
            component.courses = [];
            component.ngOnChanges();
            expect(getAllCoursesWithQuizExercisesSpy).not.toHaveBeenCalled();
            expect(findAllExamsAccessibleToUserSpy).not.toHaveBeenCalled();
            expect(component.courses).toBeArrayOfSize(0);
            expect(component.exams).toBeArrayOfSize(0);
        });
    });

    it('should set current state', () => {
        component.currentState = State.COURSE;
        component.setCurrentState(State.EXAM);
        expect(component.currentState).toEqual(State.EXAM);
    });

    describe('onCourseSelect', () => {
        it('should not set all existing questions when course is not selected', () => {
            component.existingQuestions = component.allExistingQuestions = [];
            component.selectedCourseId = undefined;
            component.onCourseSelect();
            expect(component.existingQuestions).toBeArrayOfSize(0);
            expect(component.allExistingQuestions).toBeArrayOfSize(0);
        });

        it('should set all existing questions when course is selected', () => {
            const course0 = new Course();
            course0.id = 1;
            const course1 = new Course();
            course1.id = 2;
            component.existingQuestions = component.allExistingQuestions = [];
            component.selectedCourseId = course0.id;
            component.courses = [course0, course1];
            const quizExercise = new QuizExercise(course0, undefined);
            quizExercise.id = 1;
            const quizQuestion = new MultipleChoiceQuestion();
            quizExercise.quizQuestions = [quizQuestion];
            const findForCourseSpy = jest.spyOn(quizExerciseService, 'findForCourse').mockReturnValue(of(new HttpResponse<QuizExercise[]>({ body: [quizExercise] })));
            const findSpy = jest.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
            const applyFilterSpy = jest.spyOn(component, 'applyFilter').mockImplementation();
            component.onCourseSelect();
            expect(findForCourseSpy).toHaveBeenCalledExactlyOnceWith(course0.id);
            expect(findSpy).toHaveBeenCalledExactlyOnceWith(quizExercise.id);
            expect(quizQuestion.exercise).toEqual(quizExercise);
            expect(component.allExistingQuestions).toBeArrayOfSize(1);
            expect(component.allExistingQuestions[0]).toEqual(quizQuestion);
            expect(applyFilterSpy).toHaveBeenCalledOnce();
        });
    });

    describe('onExamSelect', () => {
        it('should not set all existing questions when exam is not selected', () => {
            component.existingQuestions = component.allExistingQuestions = [];
            component.selectedExamId = undefined;
            component.onExamSelect();
            expect(component.existingQuestions).toBeArrayOfSize(0);
            expect(component.allExistingQuestions).toBeArrayOfSize(0);
        });

        it('should set all existing questions when exam is selected', () => {
            const exam0 = new Exam();
            exam0.id = 1;
            const exam1 = new Exam();
            exam1.id = 2;
            const course = new Course();
            component.existingQuestions = component.allExistingQuestions = [];
            component.selectedExamId = exam0.id;
            component.exams = [exam0, exam1];
            const quizExercise = new QuizExercise(course, undefined);
            quizExercise.id = 1;
            const quizQuestion = new MultipleChoiceQuestion();
            quizExercise.quizQuestions = [quizQuestion];
            const findForExamSpy = jest.spyOn(quizExerciseService, 'findForExam').mockReturnValue(of(new HttpResponse<QuizExercise[]>({ body: [quizExercise] })));
            const findSpy = jest.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
            const applyFilterSpy = jest.spyOn(component, 'applyFilter').mockImplementation();
            component.onExamSelect();
            expect(findForExamSpy).toHaveBeenCalledExactlyOnceWith(exam0.id);
            expect(findSpy).toHaveBeenCalledExactlyOnceWith(quizExercise.id);
            expect(quizQuestion.exercise).toEqual(quizExercise);
            expect(component.allExistingQuestions).toBeArrayOfSize(1);
            expect(component.allExistingQuestions[0]).toEqual(quizQuestion);
            expect(applyFilterSpy).toHaveBeenCalledOnce();
        });
    });

    describe('applyFilter', () => {
        const { question: multiChoiceQuestion } = createValidMCQuestion();
        const { question: dndQuestion } = createValidDnDQuestion();
        const { question: shortQuestion } = createValidSAQuestion();

        beforeEach(() => {
            component.allExistingQuestions = [multiChoiceQuestion, dndQuestion, shortQuestion];
            component.mcqFilterEnabled = false;
            component.dndFilterEnabled = false;
            component.shortAnswerFilterEnabled = false;
            component.searchQueryText = '';
        });

        it('should put mc question when mc filter selected', () => {
            component.mcqFilterEnabled = true;
            component.applyFilter();
            expect(component.existingQuestions).toEqual([multiChoiceQuestion]);
        });

        it('should put mc question when dnd filter selected', () => {
            component.dndFilterEnabled = true;
            component.applyFilter();
            expect(component.existingQuestions).toEqual([dndQuestion]);
        });

        it('should put mc question when sa filter selected', () => {
            component.shortAnswerFilterEnabled = true;
            component.applyFilter();
            expect(component.existingQuestions).toEqual([shortQuestion]);
        });

        it('should put all if all selected', () => {
            component.mcqFilterEnabled = true;
            component.dndFilterEnabled = true;
            component.shortAnswerFilterEnabled = true;
            component.applyFilter();
            expect(component.existingQuestions).toEqual(component.allExistingQuestions);
        });
    });

    describe('import questions', () => {
        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            const changeDetectorDetectChangesStub = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');
            component.setImportFile(ev);
            expect(component.importFile).toEqual(file);
            expect(component.importFileName).toBe('testFileName');
            expect(changeDetectorDetectChangesStub).toHaveBeenCalledOnce();
        });
    });

    describe('importing quiz', () => {
        let generateFileReaderStub: jest.SpyInstance;
        let getElementStub: jest.SpyInstance;
        let readAsText: jest.Mock;
        let reader: FileReader;
        const jsonContent = `[{
                "type": "multiple-choice",
                "id": 1,
                "title": "vav",
                "text": "Enter your long question if needed",
                "hint": "Add a hint here (visible during the quiz via ?-Button)",
                "score": 1,
                "scoringType": "ALL_OR_NOTHING",
                "randomizeOrder": true,
                "invalid": false,
                "answerOptions": [
                  {
                    "id": 1,
                    "text": "Enter a correct answer option here",
                    "hint": "Add a hint here (visible during the quiz via ?-Button)",
                    "explanation": "Add an explanation here (only visible in feedback after quiz has ended)",
                    "isCorrect": true,
                    "invalid": false
                  },
                  {
                    "id": 2,
                    "text": "Enter a wrong answer option here",
                    "isCorrect": false,
                    "invalid": false
                  }
                ]
              }]`;
        const fakeFile = new File([jsonContent], 'file.txt', { type: 'text/plain' });
        const questions = JSON.parse(jsonContent) as QuizQuestion[];
        const element = document.createElement('input');
        const control = { ...element, value: 'test' };
        beforeEach(() => {
            component.importFile = fakeFile;
            readAsText = jest.fn();
            reader = new FileReader();
            // @ts-ignore
            reader = { ...reader, result: jsonContent };
            // @ts-ignore
            generateFileReaderStub = jest.spyOn(component, 'generateFileReader').mockReturnValue({ ...reader, onload: null, readAsText });
            // @ts-ignore
            getElementStub = jest.spyOn(document, 'getElementById').mockReturnValue(control);
        });

        afterEach(() => {
            jest.clearAllMocks();
        });

        it('should call verify and import questions with right json', async () => {
            expect(control.value).toBe('test');
            await component.importQuiz();
            expect(readAsText).toHaveBeenCalledWith(fakeFile);
            expect(generateFileReaderStub).toHaveBeenCalledOnce();
            const addQuestionSpy = jest.spyOn(component, 'addQuestions').mockImplementation();
            await component.onFileLoadImport(reader);
            expect(addQuestionSpy).toHaveBeenCalledWith(questions);
            expect(component.importFile).toBeUndefined();
            expect(component.importFileName).toBe('');
            expect(getElementStub).toHaveBeenCalledOnce();
            expect(control.value).toBe('');
        });

        it('should not call any functions without import file', async () => {
            component.importFile = undefined;
            await component.importQuiz();
            expect(readAsText).not.toHaveBeenCalled();
            expect(generateFileReaderStub).not.toHaveBeenCalled();
            expect(component.importFile).toBeUndefined();
        });

        it('should alert user when onload throws error', async () => {
            const alert = window.alert;
            const alertFunction = jest.fn();
            window.alert = alertFunction;
            const addQuestionSpy = jest.spyOn(component, 'addQuestions');
            addQuestionSpy.mockImplementation(() => {
                throw '';
            });
            await component.importQuiz();
            await component.onFileLoadImport(reader);
            expect(alertFunction).toHaveBeenCalledOnce();
            window.alert = alert;
        });
    });

    describe('generating file reader', () => {
        it('should return file reader when called', () => {
            expect(component.generateFileReader()).toEqual(new FileReader());
        });
    });

    describe('add existing questions', () => {
        it('should call addQuestions', () => {
            const question0 = new MultipleChoiceQuestion();
            question0.exportQuiz = true;
            const question1 = new MultipleChoiceQuestion();
            question1.exportQuiz = false;
            component.existingQuestions = [question0, question1];
            const addQuestionsSpy = jest.spyOn(component, 'addQuestions').mockImplementation();
            component.addExistingQuestions();
            expect(addQuestionsSpy).toHaveBeenCalledExactlyOnceWith([question0]);
        });
    });

    describe('add questions', () => {
        it('should open modal', async () => {
            const question0 = new MultipleChoiceQuestion();
            const question1 = new MultipleChoiceQuestion();
            question0.answerOptions = [];
            question1.answerOptions = [];
            question0.invalid = false;
            question1.invalid = true;
            const shouldImportEmitter = new EventEmitter<void>();
            const componentInstance = { questions: [], shouldImport: shouldImportEmitter };
            const modalServiceSpy = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance });
            const questions = [question0, question1];
            await component.addQuestions(questions);
            expect(modalServiceSpy).toHaveBeenCalledOnce();
            shouldImportEmitter.emit();
        });

        it('should emit onQuestionsAdded', async () => {
            const question0 = new MultipleChoiceQuestion();
            question0.answerOptions = [new AnswerOption()];
            question0.invalid = false;
            const question1 = new ShortAnswerQuestion();
            const spot = new ShortAnswerSpot();
            question1.spots = [spot];
            const solution = new ShortAnswerSolution();
            question1.solutions = [solution];
            question1.correctMappings = [new ShortAnswerMapping(spot, solution)];
            question1.invalid = false;

            const dragItemFileName1 = 'item1.jpg';
            const dragItemFileName2 = 'item2.jpg';
            const backgroundFileName = 'background.png';
            const dragItemFile1 = new File([''], dragItemFileName1);
            const dragItemFile2 = new File([''], dragItemFileName2);
            const backgroundFile = new File([''], backgroundFileName);
            const question2 = new DragAndDropQuestion();
            question2.backgroundFilePath = backgroundFileName;
            const dropLocation1 = new DropLocation();
            const dropLocation2 = new DropLocation();
            question2.dropLocations = [dropLocation1, dropLocation2];
            const dragItem1 = { id: 14, pictureFilePath: dragItemFileName1, invalid: false } as DragItem;
            const dragItem2 = { id: 15, pictureFilePath: dragItemFileName2, invalid: false } as DragItem;
            question2.dragItems = [dragItem1, dragItem2];
            question2.correctMappings = [
                { dragItem: { id: 14, pictureFilePath: dragItemFileName1 } as DragItem, dropLocation: dropLocation1, invalid: false },
                { dragItem: { id: 15, pictureFilePath: dragItemFileName2 } as DragItem, dropLocation: dropLocation2, invalid: false },
            ];
            const onQuestionsAddedSpy = jest.spyOn(component.onQuestionsAdded, 'emit').mockImplementation();
            const onFilesAddedSpy = jest.spyOn(component.onFilesAdded, 'emit').mockImplementation();
            const getFileMock = jest.spyOn(fileService, 'getFile').mockResolvedValueOnce(backgroundFile).mockResolvedValueOnce(dragItemFile1).mockResolvedValueOnce(dragItemFile2);
            const questions = [question0, question1, question2];
            await component.addQuestions(questions);
            expect(onQuestionsAddedSpy).toHaveBeenCalledOnce();
            expect(onFilesAddedSpy).toHaveBeenCalledOnce();
            expect(getFileMock).toHaveBeenCalledTimes(3);
        });
    });
});
