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

export class InstructorCourseDashboardComponent implements OnInit, OnDestroy { //initializing vars needed

    course: Course;
    paramSub: Subscription;
    predicate: any;
    reverse: any;
    numberOfExercises = 0;
    results = [];
    ratedResultsArrayList = [];
    participations = [];    // [Participation]
    typeQuizExercise : Array<{firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListQ: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}> , scoreListQString: string, participated: number, successful: number }> = []; // refactored to a more legible name
    typeProgrammingExercise: Array<{firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListP: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListPString: string, participated: number, successful: number, exerciseNotCounted: boolean }> = [];
    typeModelingExercise : Array<{firstName: string, lastName: string, id: string, login: string, email: string, exType: string, totalScore: number, scoreListM: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>, scoreListMString: string, participated: number, successful: number, exerciseNotCounted: boolean }> = [];
    allQuizExercises = [];
    allProgrammingExercises = [];
    allModelingExercises = [];
    titleQuizString = '';
    titleProgrammingString = '';
    titleModelingString = '';
    maxScoreForQuizzes = 0;
    maxScoreForModeling = 0;
    maxScoreForProgramming = 0;
    finalScores: Array<{firstName: string, lastName: string, id: string, login: string, email: string, modelingTotalScore: number, modelingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, modelingScoreString: string, ProgrammingTotalScore: number, ProgrammingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, ProgrammingScoreString: string, QuizTotalScore: number, QuizEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, QuizScoreString: string, OverallScore: number, participatedQuizzes: number, participationQuizzesInPercent: number, successfulQuizzes: number, successfullyCompletedQuizzesInPercent: number, participatedProgramming: number, participationProgrammingInPercent: number, successfulProgramming: number, successfullyCompletedProgrammingInPercent: number, participatedModeling: number, participationModelingInPercent: number, successfulModeling: number, successfullyCompletedModelingInPercent: number, successfulTotal: number, successfullyCompletedTotalInPercent: number, participatedTotal: number, participationTotalInPercent: number}> = [];



    exerciseArrayQ: Array<Exercise> = []; //Todo: this will replace and renamed to allQuizExercises
    exerciseArrayP: Array<Exercise> = []; //Todo: this will replace and renamed to allProgrammingExercises
    exerciseArrayM: Array<Exercise> = []; //Todo: this will replace and renamed to allModelingExercises

    studentArray: Array<Student> = []; //Todo: this will replace the three buckets


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
        if (!this.results || !this.participations || this.participations.length === 0 || this.results.length === 0) {
            return;
        } // filtering results

        let exercisesSeen: Array<Exercise> = []; //create an array to store seen exercises

        for (const participation of this.participations) { // iterate through participations

            const ex = participation.exercise;
            let exercise : Exercise = new Exercise(ex.id, ex.title, ex.maxScore, ex.type, ex.dueDate);
            //creating exercise object
            if (!exercisesSeen.some(score => score['id'] === exercise.id )) { //matching up excercises
                exercisesSeen.push(exercise);
                this.numberOfExercises++; //increase number of exercises property to display on page

                this.getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise);

            }
        }

        console.log('length of Q: '+this.exerciseArrayQ.length+' maxScores: '+this.maxScoreForQuizzes); //TODO: remove
        console.log('length of M: '+this.exerciseArrayM.length+' maxScores: '+this.maxScoreForModeling); //TODO: remove as well
        console.log('length of P '+this.exerciseArrayP.length+' maxScores: '+this.maxScoreForProgramming); //TODO: check if needed and remove

        this.getAllScoresForAllCourseParticipants();

        for(var i = 0;i<this.studentArray.length;i++) { //TODO: check if needed and remove
            console.log(this.studentArray[i]);
        }

        console.log(this.studentArray.length); //TODO: check if needed and remove
    }

    getAllScoresForAllCourseParticipants() {

        if (!this.results || !this.participations || this.participations.length === 0 || this.results.length === 0) {
            return;
        } //filtering

        for (const result of this.results) { //iterating through results to create student and corresponding exercise objects

            const stud = result.participation.student;
            const ex = result.participation.exercise;

            let student = new Student (stud.firstName, stud.lastName, stud.id, stud.login, stud.email, 0, 0,0,[], '', [],
                '', [], '',
                0, 0, true);

            let exercise : Exercise = new Exercise (ex.id, ex.title, ex.maxScore, ex.type, ex.dueDate);

            if(!this.studentArray.some(score => score['id'] === student.id)) {
                this.studentArray.push(student);
            }

            this.getScoresForQuizzesModelingProgrammingExercises(student, exercise, result);

        }

        this.getTotalScoresForQuizzesModelingProgrammingExercises();

        this.mergeScoresForExerciseCategories();
    }

    mergeScoresForExerciseCategories() { //This method groups exercises into categories
        let finalScores : Array<{firstName: string, lastName: string, id: string, login: string, email: string, modelingTotalScore: number, modelingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, modelingScoreString: string, ProgrammingTotalScore: number, ProgrammingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, ProgrammingScoreString: string, QuizTotalScore: number, QuizEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>, QuizScoreString: string, OverallScore: number, participatedQuizzes: number, participationQuizzesInPercent: number, successfulQuizzes: number, successfullyCompletedQuizzesInPercent: number, participatedProgramming: number, participationProgrammingInPercent: number, successfulProgramming: number, successfullyCompletedProgrammingInPercent: number, participatedModeling: number, participationModelingInPercent: number, successfulModeling: number, successfullyCompletedModelingInPercent: number, successfulTotal: number, successfullyCompletedTotalInPercent: number, participatedTotal: number, participationTotalInPercent: number}> = [];

        this.typeQuizExercise.forEach((studentsQuizScore) => {

            if (!finalScores.some(student => student['id'] === studentsQuizScore.id)) { //if not existing create following
                finalScores.push({
                    'firstName': studentsQuizScore.firstName,
                    'lastName': studentsQuizScore.lastName,
                    'id': studentsQuizScore.id,
                    'login': studentsQuizScore.login,
                    'email': studentsQuizScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': [],
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': [],
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': [],
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
                });
            }

            let quizScoreString : string = ''; //initializing score string for csv export
            let quizEveryScore : Array<{exID: number, exTitle: string, absoluteScore: number}> = []; //array to save scores
            this.allQuizExercises.forEach((quiz) => {
                let bool : Boolean = true;
                const indexAllQuiz = this.allQuizExercises.findIndex( ex => ex.id === quiz.id) //TODO: semicolon is missing check if change in functionality
                const exerciseIdentifierQuiz = this.allQuizExercises[indexAllQuiz].exId;
                // refactoring done changed 4 instances of exID to something more readable
                studentsQuizScore.scoreListQ.forEach ((scoresQ) => {  // matching
                    if (exerciseIdentifierQuiz === scoresQ.exID) {
                        bool = false; //ensure to only enter the loop later once
                        quizScoreString += scoresQ.absoluteScore + ',';
                        quizEveryScore.push({
                            'exID': scoresQ.exID, 'exTitle': scoresQ.exTitle,
                            'absoluteScore': +scoresQ.absoluteScore
                        });
                    }
                });
                if (bool) {
                    quizEveryScore.push({
                        'exID': exerciseIdentifierQuiz, 'exTitle': this.allQuizExercises[indexAllQuiz].title,
                        'absoluteScore': 0
                    });
                    quizScoreString += '0,';
                }
            });

            let indexQuizFinalScores : number = this.finalScores.findIndex( student => student.id === studentsQuizScore.id);
            finalScores[indexQuizFinalScores].QuizTotalScore = studentsQuizScore.totalScore;
            finalScores[indexQuizFinalScores].QuizEveryScore = quizEveryScore;
            finalScores[indexQuizFinalScores].QuizScoreString = quizScoreString;
            finalScores[indexQuizFinalScores].participatedQuizzes = studentsQuizScore.participated;
            finalScores[indexQuizFinalScores].successfulQuizzes = studentsQuizScore.successful;
        });

        this.typeModelingExercise.forEach( (studentsModelingScore) => {

            if (!finalScores.some(student => student['id'] === studentsModelingScore.id)) {//if not existing create following
                finalScores.push({
                    'firstName': studentsModelingScore.firstName,
                    'lastName': studentsModelingScore.lastName,
                    'id': studentsModelingScore.id,
                    'login': studentsModelingScore.login,
                    'email': studentsModelingScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': [],
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': [],
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': [],
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
                });
            }

            let modelingScoreString : string = '';//initializing score string for csv export
            let modelingEveryScore : Array<{exID: number, exTitle: string, absoluteScore: number}> = [];

            this.allModelingExercises.forEach( (modelings) => { //adding up our score strings for our export
                let bool : Boolean = true;
                const indexAllModeling = this.allModelingExercises.findIndex( ex => ex.id === modelings.id) //TODO semicolon missing check if functionality change
                const exerciseIdentifierModeling = this.allModelingExercises[indexAllModeling].exId;
                studentsModelingScore.scoreListM.forEach( (scoresM) => {
                    if (exerciseIdentifierModeling === scoresM.exID) {
                        bool = false;
                        modelingScoreString += scoresM.absoluteScore + ',';
                        modelingEveryScore.push({
                            'exID': scoresM.exID, 'exTitle': scoresM.exTitle,
                            'absoluteScore': +scoresM.absoluteScore
                        });
                    }
                });
                if (bool) {
                    modelingEveryScore.push({
                        'exID': exerciseIdentifierModeling, 'exTitle': this.allModelingExercises[indexAllModeling].title,
                        'absoluteScore': 0
                    });
                    modelingScoreString += '0,';
                }
            });
            // adding temporary variables to our final scores array list
            let indexModelingFinalScores : number = this.finalScores.findIndex( student => student.id === studentsModelingScore.id);
            finalScores[indexModelingFinalScores].modelingTotalScore = studentsModelingScore.totalScore;
            finalScores[indexModelingFinalScores].modelingEveryScore = modelingEveryScore;
            finalScores[indexModelingFinalScores].modelingScoreString = modelingScoreString;
            finalScores[indexModelingFinalScores].participatedModeling = studentsModelingScore.participated;
            finalScores[indexModelingFinalScores].successfulModeling = studentsModelingScore.successful;
        });

        this.typeProgrammingExercise.forEach( (studentsProgrammingScore) => {

            if (!finalScores.some(student => student['id'] === studentsProgrammingScore.id)) {//if not existing create following
                finalScores.push({
                    'firstName': studentsProgrammingScore.firstName,
                    'lastName': studentsProgrammingScore.lastName,
                    'id': studentsProgrammingScore.id,
                    'login': studentsProgrammingScore.login,
                    'email': studentsProgrammingScore.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': [],
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': [],
                    'ProgrammingScoreString': '',
                    'modelingTotalScore': 0,
                    'modelingEveryScore': [],
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
                });
            }

            let programmingScoreString = '';//initializing score string for csv export
            let programmingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}> = [];

            this.allProgrammingExercises.forEach((programmings) => {
                let bool: Boolean = true;
                const indexAllProgramming = this.allModelingExercises.findIndex( ex => ex.id === programmings.id) //TODO semicolon missing check if functionality change
                const exerciseIdentifierProgramming = this.allModelingExercises[indexAllProgramming].exId;

                studentsProgrammingScore.scoreListP.forEach( (scoresP) => {
                    if (exerciseIdentifierProgramming === scoresP.exID) {
                        bool = false;
                        programmingScoreString += scoresP.absoluteScore + ',';
                        programmingEveryScore.push({
                            'exID': scoresP.exID, 'exTitle': scoresP.exTitle,
                            'absoluteScore': +scoresP.absoluteScore
                        });
                    }
                });
                if (bool) {
                    programmingEveryScore.push({
                        'exID': exerciseIdentifierProgramming, 'exTitle': this.allProgrammingExercises[indexAllProgramming].title,
                        'absoluteScore': 0
                    });
                    programmingScoreString += '0,';
                }
            });

            let indexProgrammingFinalScores : number = this.finalScores.findIndex( student => student.id === studentsProgrammingScore.id);
            finalScores[indexProgrammingFinalScores].ProgrammingTotalScore = studentsProgrammingScore.totalScore;
            finalScores[indexProgrammingFinalScores].ProgrammingEveryScore = programmingEveryScore;
            finalScores[indexProgrammingFinalScores].ProgrammingScoreString = programmingScoreString;
            finalScores[indexProgrammingFinalScores].participatedProgramming = studentsProgrammingScore.participated;
            finalScores[indexProgrammingFinalScores].successfulProgramming = studentsProgrammingScore.successful;
        });

        // gets all students that were not caught in results
        this.participations.forEach((participation) => {

            let modelingString = ''; // we define these strings to cover the calues needed in the export
            let quizString = '';
            let programmingString = '';
            let quizEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>=[];
            let modelingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>=[];
            let programmingEveryScore: Array<{exID: number, exTitle: string, absoluteScore: number}>=[];

            const student = participation.student;

            if (!finalScores.some(student => student['id'] === student.id)) {

                /*   console.log('do we need participation score?');
                   console.log(student.firstName);
                   console.log(student.id); */ // TODO: not needed in completion of project check usefulness and delete

                this.allModelingExercises.forEach( (modelingExercise) => { // creating objects to store information about the given exercise
                    modelingString += '0,';
                    modelingEveryScore.push({
                        'exID': modelingExercise.exID,
                        'exTitle': modelingExercise.exTitle,
                        'absoluteScore': 0
                    });
                });
                this.allQuizExercises.forEach( (quizExercise) => {
                    quizString += '0,';
                    quizEveryScore.push({
                        'exID': quizExercise.exID,
                        'exTitle': quizExercise.exTitle,
                        'absoluteScore': 0
                    });
                });
                this.allProgrammingExercises.forEach( (programmingExercise) => {
                    programmingString += '0,';
                    programmingEveryScore.push({
                        'exID': programmingExercise.exID,
                        'exTitle': programmingExercise.exTitle,
                        'absoluteScore': 0
                    });
                });

                finalScores.push({ // creating an object for each student containing needed information
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
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
                });
            } else {
                let indexOverallFinalScores : number = this.finalScores.findIndex( student => student.id === student.id);
                finalScores[indexOverallFinalScores].OverallScore = finalScores[indexOverallFinalScores].QuizTotalScore
                    + finalScores[indexOverallFinalScores].ProgrammingTotalScore + finalScores[indexOverallFinalScores].modelingTotalScore;
                finalScores[indexOverallFinalScores].successfulTotal = finalScores[indexOverallFinalScores].successfulModeling
                    + finalScores[indexOverallFinalScores].successfulProgramming + finalScores[indexOverallFinalScores].successfulQuizzes;
                finalScores[indexOverallFinalScores].participatedTotal = finalScores[indexOverallFinalScores].participatedModeling
                    + finalScores[indexOverallFinalScores].participatedProgramming + finalScores[indexOverallFinalScores].participatedQuizzes;
            }
        });
        finalScores.forEach( (finalScore) => {
            finalScore.successfullyCompletedTotalInPercent = (finalScore.successfulTotal / this.numberOfExercises) * 100;
            finalScore.participationTotalInPercent = (finalScore.participatedTotal / this.numberOfExercises) * 100;
        });

        this.finalScores = finalScores;
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

    round(value, decimals): Number { //TODO check if useful and remove if not useable
        return Number(Math.round(Number(value + 'e' + decimals)) + 'e-' + decimals);
    }

    roundWithPower(number, precision): Number {//TODO check if useful and remove if not useable
        const factor = Math.pow(10, precision);
        const tempNumber = number * factor;
        const roundedTempNumber = Math.round(tempNumber);
        return roundedTempNumber / factor;
    }



    getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise: Exercise) { // calculating max score and title
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

    getScoresForQuizzesModelingProgrammingExercises(student: Student, exercise: Exercise, result) {

        let resultCompletionDate : Date = new Date(result.completionDate);
        let dueDate : Date = new Date(exercise.dueDate);

        switch (exercise.type) {
            case 'quiz':
                if (result.rated === true) {   // There should only be 1 rated result

                    let index : number = this.studentArray.findIndex( score => score.id === student.id);
                    this.studentArray[index].participated++;
                    if (result.successful) {
                        this.studentArray[index].successful++;
                    }

                    this.studentArray[index].scoreListForQuizzes.push({
                        'resCompletionDate': resultCompletionDate,
                        'exID': exercise.id,
                        'exTitle': exercise.title,
                        'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                    });
                }
                break;

            case 'programming-exercise':
                if (resultCompletionDate.getTime() <= dueDate.getTime()) {
                    let indexStudent : number = this.studentArray.findIndex( score => score.id === student.id);
                    if(indexStudent >= 0) {
                        let indexExc: number = this.studentArray[indexStudent].scoreListForProgramming.findIndex(exc => exc.exID === exercise.id);
                        if(indexExc >= 0) {

                            let existingScore = this.studentArray[indexStudent].scoreListForProgramming[indexExc];

                            if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                                if (this.studentArray[indexStudent].exerciseNotCounted) {
                                    this.studentArray[indexStudent].participated++;
                                    this.studentArray[indexStudent].exerciseNotCounted = false;
                                }
                                if (result.successful) {
                                    this.studentArray[indexStudent].successful++;
                                }
                                this.studentArray[indexStudent].scoreListForProgramming[indexExc] = {
                                    'resCompletionDate': resultCompletionDate,
                                    'exID': exercise.id,
                                    'exTitle': exercise.title,
                                    'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                };
                            }
                        } else {
                            // TODO Check if rules are fitting to logic
                            if (this.studentArray[indexStudent].exerciseNotCounted) {
                                this.studentArray[indexStudent].participated++;
                                this.studentArray[indexStudent].exerciseNotCounted = false;
                            }
                            if (result.successful) {
                                this.studentArray[indexStudent].successful++;
                            }
                            this.studentArray[indexStudent].scoreListForProgramming.push({
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
                let indexStudent: number = this.studentArray.findIndex(score => score.id === student.id);
                if (indexStudent >= 0) {
                    let indexExc: number = this.studentArray[indexStudent].scoreListForModeling.findIndex(exc => exc.exID === exercise.id);
                    if (indexExc >= 0) {
                        let existingScore = this.studentArray[indexStudent].scoreListForModeling[indexExc];
                        if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {     // we want to have the last result
                            if (this.studentArray[indexStudent].exerciseNotCounted) {
                                this.studentArray[indexStudent].participated++;
                                this.studentArray[indexStudent].exerciseNotCounted = false;
                            }

                            if (result.successful) {
                                this.studentArray[indexStudent].successful++;
                            }

                            this.studentArray[indexStudent].scoreListForModeling[indexExc] = {
                                'resCompletionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                            };
                        }
                    } else {
                        if (this.studentArray[indexStudent].exerciseNotCounted) {
                            this.studentArray[indexStudent].participated++;
                            this.studentArray[indexStudent].exerciseNotCounted = false;
                        }

                        if (result.successful) {
                            this.studentArray[indexStudent].successful++;
                        }

                        this.studentArray[indexStudent].scoreListForModeling.push({
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

    getTotalScoresForQuizzesModelingProgrammingExercises() {

        for (const student of /*this.typeQuizExercise*/ this.studentArray) {
            let totalScoreQuizzes : number = 0;
            let totalScoreProgramming : number = 0;
            let totalScoreModeling : number = 0;

            for (const quizzes in student.scoreListForQuizzes) {
                totalScoreQuizzes += +student.scoreListForQuizzes[quizzes].absoluteScore; // TODO test if the "unary plus" in front of studentsQuizScore is achieving the goal (should fix an Addition with Number problem)
            }
            student.totalScoreQuizzes = totalScoreQuizzes;

            for (const modelings in student.scoreListForModeling) {
                totalScoreModeling += +student.scoreListForModeling[modelings].absoluteScore; // TODO test if the "unary plus" in front of studentsModelingScore is achieving the goal
            }
            student.totalScoreModeling = totalScoreModeling;

            for (const programmings in student.scoreListForProgramming) {
                totalScoreProgramming += +student.scoreListForProgramming[programmings].absoluteScore; // TODO test if the "unary plus" in front of studentsProgrammingScore is achieving the goal
            }
            student.totalScoreProgramming = totalScoreProgramming;
        }
    }

    callback() {
    }
}

class Exercise { //creating a class for exercises
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

class Student { //creating a class for students for better code quality 
    firstName: string;
    lastName: string;
    id: string;
    login: string;
    email: string;
    totalScoreQuizzes: number;
    totalScoreProgramming: number;
    totalScoreModeling: number;
    scoreListForQuizzes: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>;
    scoreListStringForQuizzes: string;
    scoreListForProgramming: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>;
    scoreListStringForProgramming: string;
    scoreListForModeling: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>;
    scoreListStringForModeling: string;
    participated: number;
    successful: number;
    exerciseNotCounted: boolean;

    constructor(firstName: string, lastName: string, id: string, login: string, email: string, totalScoreQuizzes: number,
                totalScoreProgramming: number, totalScoreModeling: number,
                scoreListForQuizzes: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>,
                scoreListStringForQuizzes: string,
                scoreListForProgramming: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>,
                scoreListStringForProgramming: string,
                scoreListForModeling: Array<{resCompletionDate: Date, exID: number, exTitle: string, absoluteScore: Number}>,
                scoreListStringForModeling: string,
                participated: number, successful: number, exerciseNotCounted: boolean) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.login = login;
        this.email = email;
        this.totalScoreQuizzes = totalScoreQuizzes;
        this.totalScoreProgramming = totalScoreProgramming;
        this.totalScoreModeling = totalScoreModeling;
        this.scoreListForQuizzes = scoreListForQuizzes;
        this.scoreListStringForQuizzes = scoreListStringForQuizzes;
        this.scoreListForProgramming = scoreListForProgramming;
        this.scoreListStringForProgramming = scoreListStringForProgramming;
        this.scoreListForModeling = scoreListForModeling;
        this.scoreListStringForModeling = scoreListStringForModeling;
        this.participated = participated;
        this.successful = successful;
        this.exerciseNotCounted = exerciseNotCounted;
    }
}
