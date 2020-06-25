import { Component, OnInit } from '@angular/core';
import * as moment from 'moment';
import { ActivatedRoute } from '@angular/router';

import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';

import { FileService } from 'app/shared/http/file.service';
import { UMLModel } from '@ls1intum/apollon';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { HttpResponse } from '@angular/common/http';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';

import { getIcon } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-participation-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss'],
})
export class ExamParticipationSummaryComponent implements OnInit {
    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    getIcon = getIcon;

    exam: Exam;
    studentExam: StudentExam;
    exercises: any[];
    submissions: any[];
    collapseKeys: any[];
    // Quiz
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();

    // mock
    umlModel: UMLModel;
    modelingExercise: ModelingExercise;
    textSubmission: TextSubmission | null;
    textSubmission2: TextSubmission | null;
    modelingSubmission: ModelingSubmission | null;
    quizSubmission: any;
    quizExercise: any;
    programmingExercise: any;
    programmingSubmission: any;
    examId: number;
    fileUploadSub: any;

    /**
     *  Submission is of Form: studentExam, Exam
     *
     *  studentExam: { exercises: [{studentParticipations: [] Submissions}], exam: {}}
     *  exam:
     */

    constructor(
        private courseCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute,
        private modelingExerciseService: ModelingExerciseService,
        private fileService: FileService,
    ) {}

    ngOnInit() {
        // TODO remove - temporary submission mocks
        this.mock();

        // map a stand alone collapseControl to each submission
        // TODO add id?
        this.collapseKeys = [];
        for (let i = 0; i < this.submissions.length; i++) {
            this.collapseKeys.push({ isCollapsed: false });
        }

        // TODO use exercises [] for problem statements, submissions [] for actual student submissions
    }

    /**
     * exportPDF Button
     */
    printPDF() {
        window.print();
    }

    /**
     *
     * @param filePath
     * File Upload Exercise
     */
    downloadFile(filePath: string) {
        this.fileService.downloadFileWithAccessToken(filePath);
    }

    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }

    mock() {
        this.modelingExerciseService.find(53).subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
            this.modelingExercise = modelingExerciseResponse.body!;
            if (this.modelingExercise.sampleSolutionModel && this.modelingExercise.sampleSolutionModel !== '') {
                this.umlModel = JSON.parse(this.modelingExercise.sampleSolutionModel);
            }
            if (this.modelingExercise.categories) {
                this.modelingExercise.categories = this.modelingExercise.categories.map((category) => JSON.parse(category));
            }
        });
        // mock text submissions
        // @ts-ignore
        this.textSubmission = {
            durationInMinutes: 1,
            type: SubmissionType.MANUAL,
            blocks: [],
            exampleSubmission: false,
            id: 0,
            submissionDate: moment(),
            submissionExerciseType: SubmissionExerciseType.TEXT,
            submitted: true,
            text:
                'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        };
        // @ts-ignore
        this.textSubmission2 = {
            durationInMinutes: 1,
            type: SubmissionType.MANUAL,
            blocks: [],
            exampleSubmission: false,
            id: 0,
            submissionDate: moment(),
            submissionExerciseType: SubmissionExerciseType.TEXT,
            submitted: true,
            text:
                'Hallo ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        };
        // @ts-ignore
        this.modelingSubmission = {
            model: '',
            submissionExerciseType: SubmissionExerciseType.MODELING,
        };

        // mock mc quiz submission (all types)
        this.quizSubmission = {
            submissionExerciseType: 'quiz',
            id: 46,
            submitted: true,
            type: 'MANUAL',
            submissionDate: '2020-06-25T13:06:25.2128146+02:00',
            submittedAnswers: [
                {
                    type: 'multiple-choice',
                    id: 7,
                    quizQuestion: {
                        type: 'multiple-choice',
                        id: 10,
                        title: 'MC Q1',
                        text: 'Enter your long question if needed',
                        hint: 'Add a hint here (visible during the quiz via ?-Button)',
                        score: 1,
                        scoringType: 'ALL_OR_NOTHING',
                        randomizeOrder: true,
                        invalid: false,
                        answerOptions: [
                            {
                                id: 14,
                                text: 'Enter a correct answer option here',
                                hint: 'Add a hint here (visible during the quiz via ?-Button)',
                                invalid: false,
                            },
                            {
                                id: 15,
                                text: 'Enter a wrong answer option here',
                                invalid: false,
                            },
                        ],
                    },
                    selectedOptions: [
                        {
                            id: 15,
                            text: 'Enter a wrong answer option here',
                            invalid: false,
                        },
                    ],
                },
                {
                    type: 'drag-and-drop',
                    id: 8,
                    quizQuestion: {
                        type: 'drag-and-drop',
                        id: 11,
                        title: 'DnD Q2',
                        text: 'Enter your long question if needed',
                        hint: 'Add a hint here (visible during the quiz via ?-Button)',
                        score: 1,
                        scoringType: 'PROPORTIONAL_WITH_PENALTY',
                        randomizeOrder: true,
                        invalid: false,
                        backgroundFilePath: '/api/files/drag-and-drop/backgrounds/11/DragAndDropBackground_2020-06-25T13-05-41-381_317a86a5.jpg',
                        dropLocations: [
                            {
                                id: 3,
                                posX: 33,
                                posY: 82,
                                width: 64,
                                height: 39,
                                invalid: false,
                            },
                            {
                                id: 4,
                                posX: 122,
                                posY: 144,
                                width: 49,
                                height: 31,
                                invalid: false,
                            },
                        ],
                        dragItems: [
                            {
                                id: 4,
                                pictureFilePath: null,
                                text: 'Text2',
                                invalid: false,
                            },
                            {
                                id: 3,
                                pictureFilePath: null,
                                text: 'Text',
                                invalid: false,
                            },
                        ],
                    },
                    mappings: [
                        {
                            id: 5,
                            invalid: false,
                            dragItem: {
                                id: 4,
                                pictureFilePath: null,
                                text: 'Text2',
                                invalid: false,
                            },
                            dropLocation: {
                                id: 3,
                                posX: 33,
                                posY: 82,
                                width: 64,
                                height: 39,
                                invalid: false,
                            },
                        },
                        {
                            id: 6,
                            invalid: false,
                            dragItem: {
                                id: 3,
                                pictureFilePath: null,
                                text: 'Text',
                                invalid: false,
                            },
                            dropLocation: {
                                id: 4,
                                posX: 122,
                                posY: 144,
                                width: 49,
                                height: 31,
                                invalid: false,
                            },
                        },
                    ],
                },
                {
                    type: 'short-answer',
                    id: 9,
                    quizQuestion: {
                        type: 'short-answer',
                        id: 12,
                        title: 'SA Q3',
                        text:
                            'Enter your long question if needed\n\nSelect a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n\nYou can define a input field like this: This [-spot 1] an [-spot 2] field.\n\nTo define the solution for the input fields you need to create a mapping (multiple mapping also possible):',
                        score: 5,
                        scoringType: 'ALL_OR_NOTHING',
                        randomizeOrder: true,
                        invalid: false,
                        spots: [
                            {
                                id: 7,
                                spotNr: 1,
                                width: 15,
                                invalid: false,
                            },
                            {
                                id: 8,
                                spotNr: 2,
                                width: 15,
                                invalid: false,
                            },
                        ],
                    },
                    submittedTexts: [
                        {
                            id: 5,
                            text: 'test 1',
                            isCorrect: false,
                            spot: {
                                id: 7,
                                spotNr: 1,
                                width: 15,
                                invalid: false,
                            },
                        },
                        {
                            id: 6,
                            text: 'test 2',
                            isCorrect: false,
                            spot: {
                                id: 8,
                                spotNr: 2,
                                width: 15,
                                invalid: false,
                            },
                        },
                    ],
                },
            ],
            adjustedSubmissionDate: '2020-06-25T11:06:25.212Z',
        };
        // mock file upload submission
        this.fileUploadSub = {
            submissionExerciseType: 'file-upload',
            id: 35,
            submitted: true,
            type: 'MANUAL',
            participation: {
                type: 'student',
                id: 31,
                initializationState: 'FINISHED',
                initializationDate: '2020-05-31T11:53:01.607532+02:00',
                exercise: {
                    type: 'file-upload',
                    id: 39,
                    title: 'hjkk-ö',
                    dueDate: '2020-06-19T18:01:49+02:00',
                    assessmentDueDate: '2020-06-26T18:01:50+02:00',
                    maxScore: 5,
                    mode: 'INDIVIDUAL',
                    presentationScoreEnabled: false,
                    course: {
                        id: 7,
                        title: 'Tessas  Test Course',
                        shortName: 'TTC',
                        studentGroupName: 'test-student',
                        teachingAssistantGroupName: 'test-tutor',
                        instructorGroupName: 'test-instructor',
                        startDate: '2020-04-19T19:01:23+02:00',
                        endDate: '2020-07-22T18:13:54+02:00',
                        onlineCourse: false,
                        maxComplaints: 3,
                        maxTeamComplaints: 3,
                        maxComplaintTimeDays: 7,
                        studentQuestionsEnabled: true,
                        registrationEnabled: false,
                        presentationScore: 0,
                        complaintsEnabled: true,
                    },
                    filePattern: 'pdf,png',
                    teamMode: false,
                    visibleToStudents: true,
                    studentAssignedTeamIdComputed: false,
                    released: true,
                    ended: true,
                },
                student: {
                    id: 2,
                    login: 'ge56fis',
                    firstName: 'Tessa Ruckstuhl',
                    email: 'ge56fis@mytum.de',
                    activated: false,
                    langKey: 'en',
                    lastNotificationRead: '2020-06-15T15:54:16+02:00',
                    name: 'Tessa Ruckstuhl',
                    participantIdentifier: 'ge56fis',
                },
                participantIdentifier: 'ge56fis',
                participantName: 'Tessa Ruckstuhl',
            },
            result: {
                id: 35,
                assessor: {
                    id: 2,
                    login: 'ge56fis',
                    firstName: 'Tessa Ruckstuhl',
                    email: 'ge56fis@mytum.de',
                    activated: false,
                    langKey: 'en',
                    lastNotificationRead: '2020-06-15T15:54:16+02:00',
                    name: 'Tessa Ruckstuhl',
                    participantIdentifier: 'ge56fis',
                },
                assessmentType: 'MANUAL',
            },
            submissionDate: '2020-06-19T17:59:56.143275+02:00',
            durationInMinutes: 27726,
            filePath: '/api/files/file-upload-exercises/39/submissions/35/image.png',
        };

        // mock programming submission
        this.programmingSubmission = {
            repositoryUrl: 'https://bitbucket.ase.in.tum.de/dashboard',
            id: 1,
            submissionExerciseType: 'programming',
        };

        // this.studentExam.exercises
        this.exercises = [];
        this.exercises.push({ problemStatement: 'PS TEXT 1', title: 'TEXT TITLE' });
        this.exercises.push({ problemStatement: 'PS MODELING 1' });
        this.exercises.push({ problemStatement: 'PS TEXT 2' });
        this.exercises.push({ problemStatement: 'PS FILE UPLOAD 1' });
        this.exercises.push({ problemStatement: 'PS QUIZ 1' });
        this.exercises.push({
            problemStatement: 'PS PROGRAMMING 1',
            id: 2,
        });

        this.submissions = [];
        this.submissions.push(this.textSubmission);
        this.submissions.push(this.modelingSubmission);
        this.submissions.push(this.textSubmission2);
        this.submissions.push(this.fileUploadSub);
        this.submissions.push(this.quizSubmission);
        this.submissions.push(this.programmingSubmission);
    }
}
