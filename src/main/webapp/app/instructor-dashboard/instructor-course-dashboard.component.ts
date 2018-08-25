import {JhiAlertService} from 'ng-jhipster';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from 'rxjs/Subscription';
import {ActivatedRoute} from '@angular/router';
import {
    Course,
    CourseParticipationService,
    CourseResultService,
    CourseService
} from '../entities/course';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [
        JhiAlertService
    ]
})

export class InstructorCourseDashboardComponent implements OnInit, OnDestroy {

    course: Course;
    paramSub: Subscription;
    predicate: any;
    reverse: any;
    numberOfExercises = 0;
    results = [];
    ratedResultsArrayList = [];
    participations = [];    // [Participation]
    typeQuizExercise : {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListQ: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}> , scoreListQString: string, participated: number, successful: number }[] = []; // refactored to a more legible name
    typeProgrammingExercise: {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListP: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListPString: string, participated: number, successful: number, exerciseNotCounted: boolean }[] = [];
    typeModelingExercise : {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListM: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListMString: string, participated: number, successful: number, exerciseNotCounted: boolean }[] = [];
    allQuizExercises = [];
    allProgrammingExercises = [];
    allModelingExercises = [];
    titleQuizString = '';
    titleProgrammingString = '';
    titleModelingString = '';
    maxScoreForQuizzes = 0;
    maxScoreForModeling = 0;
    maxScoreForProgramming = 0;
    finalScores = [];


    exerciseArrayQ: Array<Exercise> = []; //Todo: this will replace and renamed to allQuizExercises
    exerciseArrayP: Array<Exercise> = []; //Todo: this will replace and renamed to allProgrammingExercises
    exerciseArrayM: Array<Exercise> = []; //Todo: this will replace and renamed to allModelingExercises


    exportReady = false;

    constructor(private route: ActivatedRoute,
                private courseService: CourseService,
                private courseResultService: CourseResultService,
                private courseParticipationService: CourseParticipationService) {
        this.reverse = false;
        this.predicate = 'id';
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe(res => {
                this.course = res.body;
                this.getResults(this.course.id);
            });
        });
    }

    getResults(courseId: number) {
        this.courseResultService.findAll(courseId).subscribe(res => { // this call gets all information to the results in the exercises
            this.results = res;
            this.groupResults();
        });

        this.courseParticipationService.findAll(courseId).subscribe(res => { // this call gets all information to the participation in the exercises
            this.participations = res;
            this.groupResults();
            // TODO: get rid of this call because all participations are probably stored in this.results anyway, so rather get them from there (DONE)
            // participations is necessary as in results not all students are stored
        });
    }

    groupResults() {
        this.results  //FDE: why was this added here?

        if (!this.results || !this.participations || this.participations.length === 0 || this.results.length === 0) {
            return;
        }

        let exercisesSeen: Array<Exercise> = [];

        for (const participation of this.participations) {

            const ex = participation.exercise; //@Stephan, passt das hier so mit ex und exercise, oder ex weglassen?
            var exercise = new Exercise(ex.id, ex.title, ex.maxScore, ex.type, ex.dueDate);

            if (!exercisesSeen.some(score => score['id'] === exercise.id )) {
                exercisesSeen.push(exercise);
                this.numberOfExercises++;

                this.getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise);

            }
        }

        console.log('length of Q: '+this.exerciseArrayQ.length+' maxScores: '+this.maxScoreForQuizzes);
        console.log('length of M: '+this.exerciseArrayM.length+' maxScores: '+this.maxScoreForModeling);
        console.log('length of P '+this.exerciseArrayP.length+' maxScores: '+this.maxScoreForProgramming);

        this.getAllScoresForAllCourseParticipants();
    }

    getAllScoresForAllCourseParticipants() {

        if (!this.results || !this.participations || this.participations.length === 0 || this.results.length === 0) {
            return;
        }
        let studentsQuizScores : {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListQ: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}> , scoreListQString: string, participated: number, successful: number }[] = [];
        let studentsProgrammingScores : {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListP: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListPString: string, participated: number, successful: number, exerciseNotCounted: boolean }[] = [];
        let studentsModelingScores : {firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListM: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListMString: string, participated: number, successful: number, exerciseNotCounted: boolean }[] = [];

        for (const result of this.results) { // populating buckets of scores

            // TODO Types
            const student = result.participation.student;
            const exercise = result.participation.exercise;

            if (!studentsProgrammingScores.some(score => score['id'] === student.id)) {
                studentsProgrammingScores.push({
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'programming-exercise',
                    'totalScore': 0,
                    'scoreListP': [],
                    'scoreListPString': '',
                    'participated': 0,
                    'successful': 0,
                    'exerciseNotCounted': true
                });
            }
            if (!studentsQuizScores.some(score => score['id'] === student.id)) {
                studentsQuizScores.push({
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'quiz',
                    'totalScore': 0,
                    'scoreListQ': [],
                    'scoreListQString': '',
                    'participated': 0,
                    'successful': 0,
                });
            }
            if (!studentsModelingScores.some(score => score['id'] === student.id)) {
                studentsModelingScores.push({
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'modeling-exercise',
                    'totalScore': 0,
                    'scoreListM': [],
                    'scoreListMString': '',
                    'participated': 0,
                    'successful': 0,
                    'exerciseNotCounted': true
                });
            }
            let resultCompletionDate = new Date(result.completionDate);
            let dueDate = new Date(exercise.dueDate);

            switch (exercise.type) {
                case 'quiz':
                    if (result.rated === true) {   // There should only be 1 rated result

                        let index : number = studentsQuizScores.findIndex( score => score.id === student.id);
                        studentsQuizScores[index].participated++;
                        if (result.successful) {
                            studentsQuizScores[index].successful++;
                        }

                        studentsQuizScores[index].scoreListQ.push({
                            'resCompletionDate': resultCompletionDate,
                            'exID': exercise.id,
                            'exTitle': exercise.title,
                            'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                        });
                    }
                    break;

                case 'programming-exercise':
                    if (resultCompletionDate.getTime() <= dueDate.getTime()) {
                        let indexStudent : number = studentsProgrammingScores.findIndex( score => score.id === student.id);
                        if(indexStudent >= 0) {
                            let indexExc: number = studentsProgrammingScores[indexStudent].scoreListP.findIndex(exc => exc.exID === exercise.id);
                            if(indexExc >= 0) {

                                let existingScore = studentsProgrammingScores[indexStudent].scoreListP[indexExc];

                                if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                                    if (studentsProgrammingScores[indexStudent].exerciseNotCounted) {
                                        studentsProgrammingScores[indexStudent].participated++;
                                        studentsProgrammingScores[indexStudent].exerciseNotCounted = false;
                                    }
                                    if (result.successful) {
                                        studentsProgrammingScores[indexStudent].successful++;
                                    }
                                    studentsProgrammingScores[indexStudent].scoreListP[indexExc] = {
                                        'resCompletionDate': resultCompletionDate,
                                        'exID': exercise.id,
                                        'exTitle': exercise.title,
                                        'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                    };
                                }
                            } else {
                                // TODO Check if rules are fitting to logic
                                if (studentsProgrammingScores[indexStudent].exerciseNotCounted) {
                                    studentsProgrammingScores[indexStudent].participated++;
                                    studentsProgrammingScores[indexStudent].exerciseNotCounted = false;
                                }
                                if (result.successful) {
                                    studentsProgrammingScores[indexStudent].successful++;
                                }
                                studentsProgrammingScores[indexStudent].scoreListP.push({
                                    'resCompletionDate': resultCompletionDate,
                                    'exID': exercise.id,
                                    'exTitle': exercise.title,
                                    'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                });
                            }
                        }
                    }
                    break;

                case 'modeling-exercise':
                    // we can also have results (due to the manual assessment) that appear after the completion date
                    // if (completionDate.getTime() <= dueDate.getTime()) { (REMOVE?)
                        let indexStudent: number = studentsModelingScores.findIndex(score => score.id === student.id);
                        if (indexStudent >= 0) {
                            let indexExc: number = studentsModelingScores[indexStudent].scoreListM.findIndex(exc => exc.exID === exercise.id);
                            if (indexExc >= 0) {
                                let existingScore = studentsModelingScores[indexStudent].scoreListM[indexExc];
                                if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {     // we want to have the last result
                                    if (studentsModelingScores[indexStudent].exerciseNotCounted) {
                                        studentsModelingScores[indexStudent].participated++;
                                        studentsModelingScores[indexStudent].exerciseNotCounted = false;
                                    }

                                    if (result.successful) {
                                        studentsModelingScores[indexStudent].successful++;
                                    }

                                    studentsModelingScores[indexStudent].scoreListM[indexExc] = {
                                        'resCompletionDate': resultCompletionDate,
                                        'exID': exercise.id,
                                        'exTitle': exercise.title,
                                        'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                    };
                                }
                            } else {
                                if (studentsModelingScores[indexStudent].exerciseNotCounted) {
                                    studentsModelingScores[indexStudent].participated++;
                                    studentsModelingScores[indexStudent].exerciseNotCounted = false;
                                }

                                if (result.successful) {
                                    studentsModelingScores[indexStudent].successful++;
                                }

                                studentsModelingScores[indexStudent].scoreListM.push({
                                    'resCompletionDate': resultCompletionDate,
                                    'exID': exercise.id,
                                    'exTitle': exercise.title,
                                    'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                });
                            }
                        }
                    // }

                    break;
                default:
            }
        }

        this.typeQuizExercise = studentsQuizScores;
        this.typeProgrammingExercise = studentsProgrammingScores;
        this.typeModelingExercise = studentsModelingScores;

        for (const studentsQuizScore of this.typeQuizExercise) {
            let totalScore : number = 0;
            for (const quizzes in studentsQuizScore.scoreListQ) {
                totalScore += +studentsQuizScore.scoreListQ[quizzes].absoluteScore; // TODO test if the "unary plus" in front of studentsQuizScore is achieving the goal (should fix an Addition with Number problem)
            }
            studentsQuizScore.totalScore = totalScore;
        }

        for (const studentsModelingScore of this.typeModelingExercise) {
            let totalScore : number = 0;
            for (const modelings in studentsModelingScore.scoreListM) {
                totalScore += +studentsModelingScore.scoreListM[modelings].absoluteScore; // TODO test if the "unary plus" in front of studentsModelingScore is achieving the goal
            }
            studentsModelingScore.totalScore = totalScore;
        }

        for (const studentsProgrammingScore of this.typeProgrammingExercise) {
            let totalScore : number = 0;
            for (const programmings in studentsProgrammingScore.scoreListP) {
                totalScore += +studentsProgrammingScore.scoreListP[programmings].absoluteScore; // TODO test if the "unary plus" in front of studentsProgrammingScore is achieving the goal
            }
            studentsProgrammingScore.totalScore = totalScore;
        }

        this.mergeScoresForExerciseCategories();
    }

    mergeScoresForExerciseCategories() {

        const finalScores = {};
        for (const studentsQuizScore of this.typeQuizExercise) {
            let quizScoreString = '';
            let quizEveryScore = {};
            for (const quiz in this.allQuizExercises) {
                let bool = true;
                let exerciseIdentifierQuiz = this.allQuizExercises[quiz].exId;
                // refactoring done changed 4 instances of exID to something more readable
                for (const scoresQ in studentsQuizScore.scoreListQ) {
                    const exID = studentsQuizScore.scoreListQ[scoresQ].exID;
                    if (exerciseIdentifierQuiz === exID) {
                        bool = false;
                        quizScoreString += studentsQuizScore.scoreListQ[scoresQ].absoluteScore + ',';
                        quizEveryScore = {
                            'exID': exID, 'exTitle': this.allQuizExercises[quiz].title,
                            'absoluteScore': studentsQuizScore.scoreListQ[scoresQ].absoluteScore
                        };
                    }
                }
                if (bool) {
                    quizEveryScore = {
                        'exID': exerciseIdentifierQuiz, 'exTitle': this.allQuizExercises[quiz].title,
                        'absoluteScore': 0
                    };
                    quizScoreString += '0,';
                }
            }

            if (!finalScores[studentsQuizScore.id]) {
                finalScores[studentsQuizScore.id] = {
                    'firstName': studentsQuizScore.firstName,
                    'lastName': studentsQuizScore.lastName,
                    'login': studentsQuizScore.login,
                    'email': studentsQuizScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': {},
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': {},
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': {},
                    'modelingScoreString': '',
                    'OverallScore': 0,
                    'participatedQuizzes': 0,
                    'participationQuizzesInPercent': 0,
                    'successfulQuizzes': 0,
                    'successfullyCompletedQuizzesInPercent': 0,
                    'participatedProgramming': 0,
                    'participationProgrammingInPercent': 0,
                    'successfulProgramming': 0,
                    'successfullyCompletedProgrammingInPercent': 0,
                    'participatedModeling': 0,
                    'participationModelingInPercent': 0,
                    'successfulModeling': 0,
                    'successfullyCompletedModelingInPercent': 0,
                    'successfulTotal': 0,
                    'successfullyCompletedTotalInPercent': 0,
                    'participatedTotal': 0,
                    'participationTotalInPercent': 0
                };
            }
            finalScores[studentsQuizScore.id].QuizTotalScore = studentsQuizScore.totalScore;
            finalScores[studentsQuizScore.id].QuizEveryScore = quizEveryScore;
            finalScores[studentsQuizScore.id].QuizScoreString = quizScoreString;
            finalScores[studentsQuizScore.id].participatedQuizzes = studentsQuizScore.participated;
            finalScores[studentsQuizScore.id].successfulQuizzes = studentsQuizScore.successful;
        }

        for (const studentsModelingScore of this.typeModelingExercise) {

            if (!finalScores[studentsModelingScore.id]) {
                finalScores[studentsModelingScore.id] = {
                    'firstName': studentsModelingScore.firstName,
                    'lastName': studentsModelingScore.lastName,
                    'login': studentsModelingScore.login,
                    'email': studentsModelingScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': {},
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': {},
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': {},
                    'modelingScoreString': '',
                    'OverallScore': 0,
                    'participatedQuizzes': 0,
                    'participationQuizzesInPercent': 0,
                    'successfulQuizzes': 0,
                    'successfullyCompletedQuizzesInPercent': 0,
                    'participatedProgramming': 0,
                    'participationProgrammingInPercent': 0,
                    'successfulProgramming': 0,
                    'successfullyCompletedProgrammingInPercent': 0,
                    'participatedModeling': 0,
                    'participationModelingInPercent': 0,
                    'successfulModeling': 0,
                    'successfullyCompletedModelingInPercent': 0,
                    'successfulTotal': 0,
                    'successfullyCompletedTotalInPercent': 0,
                    'participatedTotal': 0,
                    'participationTotalInPercent': 0
                };
            }

            let modelingScoreString = '';
            let modelingEveryScore = {};

            for (const modelings in this.allModelingExercises) {
                let bool = true;
                const exId = this.allModelingExercises[modelings].exId;

                for (const scores in studentsModelingScore.scoreListM) {
                    const exID = studentsModelingScore.scoreListM[scores].exID;
                    if (exId === exID) {
                        bool = false;
                        modelingScoreString += studentsModelingScore.scoreListM[scores].absoluteScore + ',';
                        modelingEveryScore = {
                            'exID': exID, 'exTitle': this.allModelingExercises[modelings].title,
                            'absoluteScore': studentsModelingScore.scoreListM[scores].absoluteScore
                        };
                    }
                }
                if (bool) {
                    modelingEveryScore = {
                        'exID': exId, 'exTitle': this.allModelingExercises[modelings].title,
                        'absoluteScore': 0
                    };
                    modelingScoreString += '0,';
                }
            }

            finalScores[studentsModelingScore.id].modelingTotalScore = studentsModelingScore.totalScore;
            finalScores[studentsModelingScore.id].ModellEveryScore = modelingEveryScore;
            finalScores[studentsModelingScore.id].modelingScoreString = modelingScoreString;
            finalScores[studentsModelingScore.id].participatedModeling = studentsModelingScore.participated;
            finalScores[studentsModelingScore.id].successfulModeling = studentsModelingScore.successful;
        }

        for (const studentsProgrammingScore of this.typeProgrammingExercise) {

            if (!finalScores[studentsProgrammingScore.id]) {
                finalScores[studentsProgrammingScore.id] = {
                    'firstName': studentsProgrammingScore.firstName,
                    'lastName': studentsProgrammingScore.lastName,
                    'login': studentsProgrammingScore.login,
                    'email': studentsProgrammingScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': {},
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': {},
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': {},
                    'modelingScoreString': '',
                    'OverallScore': 0,
                    'participatedQuizzes': 0,
                    'participationQuizzesInPercent': 0,
                    'successfulQuizzes': 0,
                    'successfullyCompletedQuizzesInPercent': 0,
                    'participatedProgramming': 0,
                    'participationProgrammingInPercent': 0,
                    'successfulProgramming': 0,
                    'successfullyCompletedProgrammingInPercent': 0,
                    'participatedModeling': 0,
                    'participationModelingInPercent': 0,
                    'successfulModeling': 0,
                    'successfullyCompletedModelingInPercent': 0,
                    'successfulTotal': 0,
                    'successfullyCompletedTotalInPercent': 0,
                    'participatedTotal': 0,
                    'participationTotalInPercent': 0
                };
            }

            let programmingScoreString = '';
            let programmingEveryScore = {};

            for (const programmings in this.allProgrammingExercises) {
                let bool = true;
                const exId = this.allProgrammingExercises[programmings].exId;

                for (const scores in studentsProgrammingScore.scoreListP) {
                    const exID = studentsProgrammingScore.scoreListP[scores].exID;
                    if (exId === exID) {
                        bool = false;
                        programmingScoreString += studentsProgrammingScore.scoreListP[scores].absoluteScore + ',';
                        programmingEveryScore = {
                            'exID': exID, 'exTitle': this.allProgrammingExercises[programmings].title,
                            'absoluteScore': studentsProgrammingScore.scoreListP[scores].absoluteScore
                        };
                    }
                }
                if (bool) {
                    programmingEveryScore = {
                        'exID': exId, 'exTitle': this.allProgrammingExercises[programmings].title,
                        'absoluteScore': 0
                    };
                    programmingScoreString += '0,';
                }
            }

            finalScores[studentsProgrammingScore.id].ProgrammingTotalScore = studentsProgrammingScore.totalScore;
            finalScores[studentsProgrammingScore.id].ProgrammingEveryScore = programmingEveryScore;
            finalScores[studentsProgrammingScore.id].ProgrammingScoreString = programmingScoreString;
            finalScores[studentsProgrammingScore.id].participatedProgramming = studentsProgrammingScore.participated;
            finalScores[studentsProgrammingScore.id].successfulProgramming = studentsProgrammingScore.successful;
        }

        // gets all students that were not caught in results
        for (const participation of this.participations) {

            let modelingString = ''; // we define these strings to cover the calues needed in the export
            let quizString = '';
            let programmingString = '';
            let quizEveryScore = {};
            let modelingEveryScore = {};
            let programmingEveryScore = {};

            const student = participation.student;

            if (!finalScores[student.id]) {

                /*   console.log('do we need participation score?');
                   console.log(student.firstName);
                   console.log(student.id); */ // TODO: not needed in completion of project check usefulness and delete

                for (const modelingExercise in this.allModelingExercises) { // creating objects to store information about the given exercise
                    modelingString += '0,';
                    modelingEveryScore = {
                        'exID': this.allModelingExercises[modelingExercise].exID,
                        'exTitle': this.allModelingExercises[modelingExercise].title,
                        'absoluteScore': 0
                    };
                }
                for (const quizExercise in this.allQuizExercises) {
                    quizString += '0,';
                    quizEveryScore = {
                        'exID': this.allQuizExercises[quizExercise].exID,
                        'exTitle': this.allQuizExercises[quizExercise].title,
                        'absoluteScore': 0
                    };
                }
                for (const programmingExercise in this.allProgrammingExercises) {
                    programmingString += '0,';
                    programmingEveryScore = {
                        'exID': this.allProgrammingExercises[programmingExercise].exID,
                        'exTitle': this.allProgrammingExercises[programmingExercise].title,
                        'absoluteScore': 0
                    };
                }

                finalScores[student.id] = { // creating an object for each student containing needed information
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'login': student.login,
                    'email': student.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': quizEveryScore,
                    'QuizScoreString': quizString,
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': programmingEveryScore,
                    'ProgrammingScoreString': programmingString,
                    'modelingTotalScore': 0,
                    'modelingEveryScore': modelingEveryScore,
                    'modelingScoreString': modelingString,
                    'OverallScore': 0,
                    'participatedQuizzes': 0,
                    'participationQuizzesInPercent': 0,
                    'successfulQuizzes': 0,
                    'successfullyCompletedQuizzesInPercent': 0,
                    'participatedProgramming': 0,
                    'participationProgrammingInPercent': 0,
                    'successfulProgramming': 0,
                    'successfullyCompletedProgrammingInPercent': 0,
                    'participatedModeling': 0,
                    'participationModelingInPercent': 0,
                    'successfulModeling': 0,
                    'successfullyCompletedModelingInPercent': 0,
                    'successfulTotal': 0,
                    'successfullyCompletedTotalInPercent': 0,
                    'participatedTotal': 0,
                    'participationTotalInPercent': 0
                };
            } else {
                finalScores[student.id].OverallScore = finalScores[student.id].QuizTotalScore
                    + finalScores[student.id].ProgrammingTotalScore + finalScores[student.id].modelingTotalScore;
                finalScores[student.id].successfulTotal = finalScores[student.id].successfulModeling
                    + finalScores[student.id].successfulProgramming + finalScores[student.id].successfulQuizzes;
                finalScores[student.id].participatedTotal = finalScores[student.id].participatedModeling
                    + finalScores[student.id].participatedProgramming + finalScores[student.id].participatedQuizzes;
            }
        }
        for (const finalScore in finalScores) {
            finalScores[finalScore].successfullyCompletedTotalInPercent = (finalScores[finalScore].successfulTotal / this.numberOfExercises) * 100;
            finalScores[finalScore].participationTotalInPercent = (finalScores[finalScore].participatedTotal / this.numberOfExercises) * 100;
        }

        this.finalScores = Object.keys(finalScores).map(key => finalScores[key]);
        this.exportReady = true;
    }

    exportResults() { // method for exporting the csv with the needed data

        this.getAllScoresForAllCourseParticipants();

        if (this.exportReady && this.finalScores.length > 0) {
            const rows = [];
            this.finalScores.forEach((result, index) => {

                const firstName = result.firstName.trim();
                const lastName = result.lastName.trim();
                const studentId = result.login.trim();
                const email = result.email.trim();
                const quiz = result.QuizTotalScore;
                const programming = result.ProgrammingTotalScore;
                const modeling = result.modelingTotalScore;
                const score = result.OverallScore;
                const quizString = result.QuizScoreString;
                const modelingString = result.modelingScoreString;
                const programmingString = result.ProgrammingScoreString;
                if (index === 0) {
                    const info = 'data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,QuizTotalScore,'; // shortening line length and complexity
                    rows.push(info + this.titleQuizString + 'ProgrammingTotalScore,' + this.titleProgrammingString + 'modelingTotalScore,' + this.titleModelingString + 'OverallScore');
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quiz + ',' + quizString + '' + programming + ',' + programmingString + '' + modeling + ',' + modelingString + '' + score);
                } else {
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quiz + ',' + quizString + '' + programming + ',' + programmingString + '' + modeling + ',' + modelingString + '' + score);
                }
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'course_' + this.course.title + '-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
            console.log(this.results);

            console.log(this.ratedResultsArrayList);
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }

    round(value, decimals): Number {
        return Number(Math.round(Number(value + 'e' + decimals)) + 'e-' + decimals);
    }

    roundWithPower(number, precision): Number {
        const factor = Math.pow(10, precision);
        const tempNumber = number * factor;
        const roundedTempNumber = Math.round(tempNumber);
        return roundedTempNumber / factor;
    }



    getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise: Exercise) {
        switch (exercise.type) {
            case 'quiz':
                this.maxScoreForQuizzes += exercise.maxScore;
                this.exerciseArrayQ.push(exercise);
                this.titleQuizString += exercise.title + ',';
                break;

            case 'programming-exercise':
                this.maxScoreForProgramming += exercise.maxScore;
                this.exerciseArrayP.push(exercise);
                this.titleProgrammingString += exercise.title + ',';
                break;

            case 'modeling-exercise':
                this.maxScoreForModeling += exercise.maxScore;
                this.exerciseArrayM.push(exercise);
                this.titleModelingString += exercise.title + ',';
                break;

            default:
        }
    }

    callback() {
    }
}

class Exercise {
    id: number;
    title: string;
    maxScore: number;
    type: string;
    dueDate: Date;

    constructor(exerciseID: number, exerciseTitle: string, exerciseMaxScore: number,
                exerciseType: string, exerciseDueDate: Date){
        this.id = exerciseID;
        this.title = exerciseTitle;
        this.maxScore = exerciseMaxScore;
        this.type = exerciseType;
        this.dueDate = exerciseDueDate;
    }


}
