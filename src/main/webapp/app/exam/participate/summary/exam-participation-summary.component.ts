import { Component, OnInit } from '@angular/core';
import * as moment from 'moment';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { Exam } from 'app/entities/exam.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { FileService } from 'app/shared/http/file.service';
import { Result } from 'app/entities/result.model';
import { UMLModel } from '@ls1intum/apollon';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { HttpResponse } from '@angular/common/http';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-participation-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss'],
})
export class ExamParticipationSummaryComponent implements OnInit {
    // Quiz
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    submissions: any[];
    // mock
    course: Course | null;
    courseId: number;
    private paramSubscription: Subscription;
    result: Result | null;
    umlModel: UMLModel;
    modelingExercise: ModelingExercise;
    textSubmission: TextSubmission | null;
    textSubmission2: TextSubmission | null;
    modelingSubmission: ModelingSubmission | null;
    quizSubmission: any;
    quizExercise: any;
    programmingExercise: any;
    programmingSubmission: any;
    exam: Exam;
    examId: number;
    fileUploadEx: any;

    constructor(
        private courseCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute,
        private modelingExerciseService: ModelingExerciseService,
        private participationService: ParticipationService,
        private quizExerciseService: QuizExerciseService,
        private fileService: FileService,
    ) {}

    ngOnInit() {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);
        });

        // load exam like this until service is ready
        this.course = this.courseCalculationService.getCourse(this.courseId);
        this.exam = this.course!.exams.filter((exam) => exam.id === this.examId)[0]!;
        this.mock();
        this.submissions.map((obj) => ({ ...obj, isCollapsed: 'false' }));
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

    /**
     * Quiz Exercise
     * TODO remove any type for question and answer
     */

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
                'Hallo Mausi ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        };
        // @ts-ignore
        this.modelingSubmission = {
            model: '',
            submissionExerciseType: SubmissionExerciseType.MODELING,
        };

        // mock quiz exercise
        this.quizExercise = {
            type: 'quiz',
            id: 60,
            title: 'Test Q 1',
            releaseDate: '2020-06-20T09:52:41.000Z',
            dueDate: '2020-06-20T10:02:41.000Z',
            course: {
                id: 1,
                title: 'Einführung in die Softwaretechnik',
                description: 'Test1',
                shortName: 'EIST',
                studentGroupName: 'artemis-dev',
                teachingAssistantGroupName: 'artemis-dev',
                instructorGroupName: 'artemis-dev',
                startDate: '2020-04-07T00:00:00+02:00',
                endDate: '2020-07-22T18:13:54+02:00',
                onlineCourse: true,
                maxComplaints: 3,
                maxTeamComplaints: 3,
                maxComplaintTimeDays: 7,
                studentQuestionsEnabled: true,
            },
            randomizeQuestionOrder: true,
            isVisibleBeforeStart: true,
            isOpenForPractice: false,
            isPlannedToStart: true,
            duration: 600,
            quizQuestions: [
                {
                    type: 'short-answer',
                    id: 5,
                    title: 'SA Q',
                    text:
                        'Enter your long question if needed\n\nSelect a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n\nYou can define a input field like this: This [-spot 1] an [-spot 2] field.\n\nTo define the solution for the input fields you need to create a mapping (multiple mapping also possible):',
                    score: 1,
                    scoringType: 'ALL_OR_NOTHING',
                    randomizeOrder: true,
                    invalid: false,
                    spots: [
                        {
                            id: 3,
                            spotNr: 1,
                            width: 15,
                            invalid: false,
                        },
                        {
                            id: 4,
                            spotNr: 2,
                            width: 15,
                            invalid: false,
                        },
                    ],
                },
                {
                    type: 'multiple-choice',
                    id: 6,
                    title: 'MC2',
                    text: 'Enter your long question if needed',
                    hint: 'Add a hint here (visible during the quiz via ?-Button)',
                    score: 1,
                    scoringType: 'ALL_OR_NOTHING',
                    randomizeOrder: true,
                    invalid: false,
                    answerOptions: [
                        {
                            id: 9,
                            text: 'Enter a correct answer option here',
                            hint: 'Add a hint here (visible during the quiz via ?-Button)',
                            invalid: false,
                        },
                        {
                            id: 11,
                            text: 'Enter a wrong answer option here',
                            invalid: false,
                        },
                        {
                            id: 10,
                            text: 'Enter a wrong answer option here',
                            invalid: false,
                        },
                    ],
                },
            ],
            started: true,
            visibleToStudents: true,
            remainingTime: 596,
            ended: false,
            timeUntilPlannedStart: -3,
            adjustedDueDate: '2020-06-20T10:02:40.737Z',
        };
        // mock mc quiz submission
        this.quizSubmission = {
            submissionExerciseType: 'quiz',
            submitted: false,
            submittedAnswers: [
                {
                    type: 'multiple-choice',
                    quizQuestion: {
                        type: 'multiple-choice',
                        id: 6,
                        title: 'MC2',
                        text: 'Enter your long question if needed',
                        hint: 'Add a hint here (visible during the quiz via ?-Button)',
                        score: 1,
                        scoringType: 'ALL_OR_NOTHING',
                        randomizeOrder: true,
                        invalid: false,
                        answerOptions: [
                            {
                                id: 9,
                                text: 'Enter a correct answer option here',
                                hint: 'Add a hint here (visible during the quiz via ?-Button)',
                                invalid: false,
                            },
                            {
                                id: 11,
                                text: 'Enter a wrong answer option here',
                                invalid: false,
                            },
                            {
                                id: 10,
                                text: 'Enter a wrong answer option here',
                                invalid: false,
                            },
                        ],
                    },
                    selectedOptions: [
                        {
                            id: 9,
                            text: 'Enter a correct answer option here',
                            hint: 'Add a hint here (visible during the quiz via ?-Button)',
                            invalid: false,
                        },
                        {
                            id: 11,
                            text: 'Enter a wrong answer option here',
                            invalid: false,
                        },
                    ],
                },
                {
                    type: 'short-answer',
                    quizQuestion: {
                        type: 'short-answer',
                        id: 5,
                        title: 'SA Q',
                        text:
                            'Enter your long question if needed\n\nSelect a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n\nYou can define a input field like this: This [-spot 1] an [-spot 2] field.\n\nTo define the solution for the input fields you need to create a mapping (multiple mapping also possible):',
                        score: 1,
                        scoringType: 'ALL_OR_NOTHING',
                        randomizeOrder: true,
                        invalid: false,
                        spots: [
                            {
                                id: 3,
                                spotNr: 1,
                                width: 15,
                                invalid: false,
                            },
                            {
                                id: 4,
                                spotNr: 2,
                                width: 15,
                                invalid: false,
                            },
                        ],
                    },
                    submittedTexts: [
                        {
                            text: 'is',
                            spot: {
                                id: 3,
                                spotNr: 1,
                                width: 15,
                                invalid: false,
                            },
                        },
                        {
                            text: 'input',
                            spot: {
                                id: 4,
                                spotNr: 2,
                                width: 15,
                                invalid: false,
                            },
                        },
                    ],
                },
            ],
            submissionDate: '2020-06-20T09:53:00.848Z',
            adjustedSubmissionDate: '2020-06-20T09:53:00.848Z',
        };

        // mock sa quiz submission

        // mock file upload submission
        this.fileUploadEx = {
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
        this.programmingExercise = {
            id: 1197,
        };

        this.programmingSubmission = {
            repositoryUrl: 'https://ge93hig@bitbucket.ase.in.tum.de/scm/EIST20H02E03/eist20h02e03-ge93hig.git',
            id: 332562,
            submissionExerciseType: 'programming',
        };

        this.submissions = [];
        this.submissions.push(this.textSubmission);
        this.submissions.push(this.modelingSubmission);
        this.submissions.push(this.textSubmission2);
        this.submissions.push(this.fileUploadEx);
        this.submissions.push(this.quizSubmission);
        this.submissions.push(this.programmingSubmission);
    }
}
