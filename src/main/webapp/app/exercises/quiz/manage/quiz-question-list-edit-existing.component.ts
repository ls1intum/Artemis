import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, ViewEncapsulation } from '@angular/core';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizConfirmImportInvalidQuestionsModalComponent } from 'app/exercises/quiz/manage/quiz-confirm-import-invalid-questions-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { onError } from 'app/shared/util/global.utils';
import { checkForInvalidFlaggedQuestions } from 'app/exercises/quiz/shared/quiz-manage-util.service';

export enum State {
    COURSE = 'Course',
    EXAM = 'Exam',
    FILE = 'File',
}

@Component({
    selector: 'jhi-quiz-question-list-edit-existing',
    templateUrl: './quiz-question-list-edit-existing.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizQuestionListEditExistingComponent implements OnChanges {
    @Input() show: boolean;
    @Input() courseId: number;

    @Output() onQuestionsAdded = new EventEmitter<Array<QuizQuestion>>();

    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly State = State;
    readonly stateTexts = ['artemisApp.quizExercise.fromACourse', 'artemisApp.quizExercise.fromAnExam', 'artemisApp.quizExercise.fromAFile'];

    currentState: State = State.COURSE;
    selectedCourseId?: number;
    selectedExamId?: number;
    courses: Course[] = [];
    exams: Exam[] = [];
    allExistingQuestions: QuizQuestion[] = [];
    existingQuestions: QuizQuestion[] = [];
    searchQueryText: string;
    dndFilterEnabled = true;
    mcqFilterEnabled = true;
    shortAnswerFilterEnabled = true;
    importFile?: File;
    importFileName: string;

    constructor(
        private modalService: NgbModal,
        private fileUploaderService: FileUploaderService,
        private courseManagementService: CourseManagementService,
        private examManagementService: ExamManagementService,
        private quizExerciseService: QuizExerciseService,
        private alertService: AlertService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    ngOnChanges(): void {
        if (this.show) {
            this.courseManagementService.getAllCoursesWithQuizExercises().subscribe((res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.changeDetectorRef.detectChanges();
            });
            this.examManagementService.findAllExamsAccessibleToUser(this.courseId!).subscribe((res: HttpResponse<Exam[]>) => {
                this.exams = res.body!;
                this.changeDetectorRef.detectChanges();
            });
        }
    }

    setCurrentState(state: State) {
        this.currentState = state;
    }

    /**
     * Callback function for when a user selected a Course from the Dropdown list from 'Add existing questions'
     * Populates list of quiz exercises for the selected course
     */
    onCourseSelect() {
        this.allExistingQuestions = this.existingQuestions = [];
        if (!this.selectedCourseId) {
            return;
        }
        const selectedCourse = this.courses.find((course) => course.id === Number(this.selectedCourseId))!;
        this.quizExerciseService.findForCourse(selectedCourse.id!).subscribe({
            next: (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Callback function for when a user selected an Exam from the Dropdown list from 'Add existing questions'
     * Populates list of quiz exercises for the selected exam
     */
    onExamSelect(): void {
        this.allExistingQuestions = this.existingQuestions = [];
        if (!this.selectedExamId) {
            return;
        }

        /** Search the selected exam by id in all available exams **/
        const selectedExam = this.exams.find((exam) => exam.id === Number(this.selectedExamId))!;

        // For the given exam, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise
        this.quizExerciseService.findForExam(selectedExam.id!).subscribe({
            next: (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Applies filter on questions shown in add existing questions view.
     */
    applyFilter() {
        this.existingQuestions = [];
        for (const question of this.allExistingQuestions) {
            if (!this.searchQueryText || this.searchQueryText === '' || question.title!.toLowerCase().indexOf(this.searchQueryText.toLowerCase()) !== -1) {
                if (this.mcqFilterEnabled && question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    this.existingQuestions.push(question);
                }
                if (this.dndFilterEnabled && question.type === QuizQuestionType.DRAG_AND_DROP) {
                    this.existingQuestions.push(question);
                }
                if (this.shortAnswerFilterEnabled && question.type === QuizQuestionType.SHORT_ANSWER) {
                    this.existingQuestions.push(question);
                }
            }
        }
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Assigns the uploaded import file
     * @param event object containing the uploaded file
     */
    setImportFile(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            this.importFile = fileList[0];
            this.importFileName = this.importFile.name;
        }
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Imports a json quiz file and adds questions to current quiz exercise.
     */
    async importQuiz() {
        if (!this.importFile) {
            return;
        }
        const fileReader = this.generateFileReader();
        fileReader.onload = () => this.onFileLoadImport(fileReader);
        fileReader.readAsText(this.importFile);
    }

    async onFileLoadImport(fileReader: FileReader) {
        try {
            const questions = JSON.parse(fileReader.result as string) as QuizQuestion[];
            await this.addQuestions(questions);
            // Clearing html elements,
            this.importFile = undefined;
            this.importFileName = '';
            const control = document.getElementById('importFileInput') as HTMLInputElement;
            if (control) {
                control.value = '';
            }
        } catch (e) {
            alert('Import Quiz Failed! Invalid quiz file.');
        }
    }

    /**
     * Move file reader creation to separate function to be able to mock
     * https://fromanegg.com/post/2015/04/22/easy-testing-of-code-involving-native-methods-in-javascript/
     */
    generateFileReader() {
        return new FileReader();
    }

    /**
     * Adds selected quizzes to current quiz exercise
     */
    async addExistingQuestions() {
        const quizQuestions: QuizQuestion[] = [];
        for (const question of this.existingQuestions) {
            if (question.exportQuiz) {
                quizQuestions.push(question);
            }
        }
        this.selectedCourseId = undefined;
        this.selectedExamId = undefined;
        this.allExistingQuestions = this.existingQuestions = [];
        await this.addQuestions(quizQuestions);
    }

    /**
     * Adds given questions to current quiz exercise.
     * Ids are removed from new questions so that new id is assigned upon saving the quiz exercise.
     * Caution: All "invalid" flags are also removed.
     * Images are duplicated for drag and drop questions.
     * @param questions list of questions
     */
    async addQuestions(quizQuestions: Array<QuizQuestion>) {
        const invalidQuizQuestionMap = checkForInvalidFlaggedQuestions(quizQuestions);
        const validQuizQuestions = quizQuestions.filter((quizQuestion) => !invalidQuizQuestionMap[quizQuestion.id!]);
        const invalidQuizQuestions = quizQuestions.filter((quizQuestion) => invalidQuizQuestionMap[quizQuestion.id!]);
        let newQuizQuestions = validQuizQuestions;
        if (invalidQuizQuestions.length > 0) {
            const modal = this.modalService.open(QuizConfirmImportInvalidQuestionsModalComponent, {
                keyboard: true,
                size: 'lg',
            });
            modal.componentInstance.invalidFlaggedQuestions = invalidQuizQuestions.map((question, index) => {
                return {
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements',
                    translateValues: { index: index + 1 },
                };
            });
            modal.componentInstance.shouldImport.subscribe(async () => {
                newQuizQuestions = newQuizQuestions.concat(invalidQuizQuestions);
                newQuizQuestions = await this.convertExistingQuestionToNewQuestion(newQuizQuestions);
                this.onQuestionsAdded.emit(newQuizQuestions);
            });
        } else {
            newQuizQuestions = await this.convertExistingQuestionToNewQuestion(newQuizQuestions);
            this.onQuestionsAdded.emit(newQuizQuestions);
        }
    }

    /**
     * Find all QuizQuestions that belong to the given list of QuizExercise and add them to allExistingQuestions. Then, call applyFilter
     *
     * @param quizExercises the list of QuizExercise of which the QuizQuestions are to be retrieved and added
     */
    private applyQuestionsAndFilter(quizExercises: QuizExercise[]) {
        for (const quizExercise of quizExercises) {
            this.quizExerciseService.find(quizExercise.id!).subscribe((response: HttpResponse<QuizExercise>) => {
                const quizExerciseResponse = response.body!;
                if (quizExerciseResponse.quizQuestions && quizExerciseResponse.quizQuestions.length > 0) {
                    for (const question of quizExerciseResponse.quizQuestions) {
                        question.exercise = quizExercise;
                        this.allExistingQuestions.push(question);
                    }
                }
                this.applyFilter();
            });
        }
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Convert the given list of existing QuizQuestions to a list of new QuizQuestions
     *
     * @param existingQuizQuestions the list of existing QuizQuestions to be converted
     * @return the list of new QuizQuestions
     */
    private async convertExistingQuestionToNewQuestion(existingQuizQuestions: Array<QuizQuestion>): Promise<Array<QuizQuestion>> {
        const newQuizQuestions = new Array<QuizQuestion>();
        // To make sure all questions are duplicated (new resources are created), we need to remove some fields from the input questions,
        // This contains removing all ids, duplicating images in case of dnd questions, the question statistic and the exercise
        for (const question of existingQuizQuestions) {
            // do not set question.exercise = this.quizExercise, because it will cause a cycle when converting to json
            question.exercise = undefined;
            question.quizQuestionStatistic = undefined;
            question.invalid = false;
            question.id = undefined;
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                mcQuestion.answerOptions!.forEach((answerOption) => {
                    answerOption.id = undefined;
                    answerOption.invalid = false;
                });
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                // Get image from the old question and duplicate it on the server and then save new image to the question,
                let fileUploadResponse = await this.fileUploaderService.duplicateFile(dndQuestion.backgroundFilePath!);
                dndQuestion.backgroundFilePath = fileUploadResponse.path;

                // For DropLocations, DragItems and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by server. The server removes tempID and generated a new id,
                dndQuestion.dropLocations!.forEach((dropLocation) => {
                    dropLocation.tempID = dropLocation.id;
                    dropLocation.id = undefined;
                    dropLocation.invalid = false;
                });
                for (const dragItem of dndQuestion.dragItems || []) {
                    // Duplicating image on server. This is only valid for image drag items. For text drag items, pictureFilePath is undefined,
                    if (dragItem.pictureFilePath) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(dragItem.pictureFilePath);
                        dragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    dragItem.tempID = dragItem.id;
                    dragItem.id = undefined;
                    dragItem.invalid = false;
                }
                for (const correctMapping of dndQuestion.correctMappings || []) {
                    // Following fields are not required for dnd question. They will be generated by the server,
                    correctMapping.id = undefined;
                    correctMapping.dragItemIndex = undefined;
                    correctMapping.dropLocationIndex = undefined;
                    correctMapping.invalid = false;

                    // Duplicating image on server. This is only valid for image drag items. For text drag items, pictureFilePath is undefined,
                    const correctMappingDragItem = correctMapping.dragItem!;
                    if (correctMappingDragItem.pictureFilePath) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(correctMappingDragItem.pictureFilePath);
                        correctMappingDragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    correctMappingDragItem.tempID = correctMappingDragItem?.id;
                    correctMapping.dragItem!.id = undefined;
                    correctMapping.dropLocation!.tempID = correctMapping.dropLocation!.id;
                    correctMapping.dropLocation!.id = undefined;
                }
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;

                // For Spots, Solutions and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by server. The server removes tempID and generated a new id,
                shortAnswerQuestion.spots!.forEach((spot) => {
                    spot.tempID = spot.id;
                    spot.id = undefined;
                    spot.invalid = false;
                });
                shortAnswerQuestion.solutions!.forEach((solution) => {
                    solution.tempID = solution.id;
                    solution.id = undefined;
                    solution.invalid = false;
                });
                shortAnswerQuestion.correctMappings!.forEach((correctMapping) => {
                    // Following fields are not required for short answer question. They will be generated by the server,
                    correctMapping.id = undefined;
                    correctMapping.shortAnswerSolutionIndex = undefined;
                    correctMapping.shortAnswerSpotIndex = undefined;
                    correctMapping.invalid = false;

                    correctMapping.solution!.tempID = correctMapping.solution!.id;
                    correctMapping.solution!.id = undefined;
                    correctMapping.spot!.tempID = correctMapping.spot!.id;
                    correctMapping.spot!.id = undefined;
                });
            }
            newQuizQuestions.push(question);
        }
        return newQuizQuestions;
    }
}
