import {JhiAlertService} from 'ng-jhipster';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from 'rxjs/Subscription';
import {ActivatedRoute} from '@angular/router';
import {
    Course,
    CourseService
} from '../entities/course';
import { exercisePopupRoute } from 'app/entities/exercise';

// using own ExerciseTypes enum, since we are not able to iterate through the original const enum ExerciseType
export enum ExerciseTypes {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
}

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
    numberOfExercises: number = 0;
    results = [];
    ratedResultsArrayList = []; // not in use - planned for something?
    participations = [];    // [Participation]
    titleQuizString: string = '';
    titleProgrammingString: string = '';
    titleModelingString: string = '';
    maxScoreForQuizzes: number = 0; // needed
    maxScoreForModeling: number = 0; // needed
    maxScoreForProgramming: number = 0; // needed
    //finalScores: Array<Student> = []; not in use - not needed
    allExercises: Map<ExerciseTypes, Array<Exercise>> = new Map<ExerciseTypes, Array<Exercise>>([]);
    allQuizExercises: Array<Exercise> = [];
    allProgrammingExercises: Array<Exercise> = [];
    allModelingExercises: Array<Exercise> = [];
    studentArray: Array<Student> = [];
    exportReady: Boolean = false;


    constructor(private route: ActivatedRoute,
                private courseService: CourseService) {
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
        this.courseService.findAllResults(courseId).subscribe(res => { // this call gets all information to the results in the exercises
            this.results = res;
            this.groupResults();
        });

        // this.courseService.findAllParticipations(courseId).subscribe(res => { // this call gets all information to the participation in the exercises
        //     this.participations = res;
        //     this.groupResults();
        //     // TODO: get rid of this call because all participations are probably stored in this.results anyway, so rather get them from there (DONE)
        //     // participations is necessary as in results not all students are stored
        // });
    }

    // Updated to Maps - pls check
    groupResults() {
        if (!this.results || this.results.length === 0) {
            return;
        } // filtering results

        // iterating through the results means, we are not considerng exercises that nobody worked on
        this.results.forEach( (result) => { // iterate through results

            let exercise : Exercise = new Exercise(result.exercise.id, result.exercise.title, result.exercise.maxScore, result.exercise.type, result.exercise.dueDate);
            // creating exercise object
            if (!this.allExercises[exercise.type].some( ex => ex['id'] === exercise.id)) { // matching up excercises
                this.numberOfExercises++; // to show the count of exercises to display on page
                this.getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise);
            }
        });

        console.log('length of Q: '+this.allQuizExercises.length+' maxScores: '+this.maxScoreForQuizzes); // TODO: remove
        console.log('length of M: '+this.allModelingExercises.length+' maxScores: '+this.maxScoreForModeling); // TODO: remove as well
        console.log('length of P '+this.allProgrammingExercises.length+' maxScores: '+this.maxScoreForProgramming); // TODO: check if needed and remove

        this.getAllScoresForAllCourseParticipants();

        for(var i = 0;i<this.studentArray.length;i++) { // TODO: check if needed and remove
            console.log(this.studentArray[i]);
        }
        console.log(this.studentArray.length); // TODO: check if needed and remove
    }

    // Updated to Maps - pls check
    getAllScoresForAllCourseParticipants() {

        if (!this.results || this.results.length === 0) {
            return;
        } // filtering

        this.results.forEach( (result) => { // iterating through results to create student and corresponding exercise objects

            let stud = result.participation.student;
            let ex = result.participation.exercise;

            let student = new Student (stud.firstName, stud.lastName, stud.id, stud.login, stud.email, 0, 0,0, new Map<ExerciseTypes, Array<Score>>([]),new Map<ExerciseTypes, number>(), [], [],'', [],
                [],'', [], [],'',
                0,0,0, 0,0,0,
                0,0,0,0,0,0,0,0,
                true,0);

            let exercise : Exercise = new Exercise (ex.id, ex.title, ex.maxScore, ex.type, ex.dueDate);

            if(!this.studentArray.some(score => score['id'] === student.id)) {
                this.studentArray.push(student);
            }

            this.getScoresForQuizzesModelingProgrammingExercises(student, exercise, result);

        });

        this.getTotalScoresForQuizzesModelingProgrammingExercises();

        this.mergeScoresForExerciseCategories();
    }

    // Updated to Maps - pls check
    mergeScoresForExerciseCategories() { // This method groups exercises into categories

        this.studentArray.forEach((student, indexStudent) => {

            let quizScoreString : string = ''; // initializing score string for csv export
            let quizEveryScore : Array<Score> = []; // array to save scores
            
            let modelingScoreString : string = '';// initializing score string for csv export
            let modelingEveryScore : Array<Score> = [];

            let programmingScoreString = '';// initializing score string for csv export
            let programmingEveryScore: Array<Score> = [];

            for ( let exType in ExerciseTypes ){
                this.allExercises[exType].forEach((exercise) => {
                    let bool : Boolean = true;
                    student.allExercises[exType].array.forEach( (score) => {
                        if (exercise.id === score.exID) {
                            bool = false; // ensure to only enter the loop later once
                            switch (exercise.type){
                                case 'quiz': 
                                    quizScoreString += score.absoluteScore + ',';
                                    quizEveryScore.push( new Score( score.resCompletionDate, score.exID, score.exTitle, +score.absoluteScore));
                                    break;
                                case 'programming-exercise':
                                    programmingScoreString += score.absoluteScore + ',';
                                    programmingEveryScore.push( new Score( score.resCompletionDate, score.exID, score.exTitle, +score.absoluteScore));
                                    break;
                                case 'modeling-exercise':
                                    modelingScoreString += score.absoluteScore + ',';
                                    modelingEveryScore.push(new Score( score.resCompletionDate, score.exID, score.exTitle, +score.absoluteScore));
                                    break;
                                default: break;
                            }
                        }
                    });
                    if (bool) {
                        switch (exercise.type){
                            case 'quiz': 
                                quizEveryScore.push(new Score( null, exercise.id, exercise .title, 0));
                                quizScoreString += '0,';                                
                                break;
                            case 'programming-exercise':
                                programmingEveryScore.push(new Score( null, exercise.id, exercise.title, 0));
                                programmingScoreString += '0,';                                
                                break;
                            case 'modeling-exercise':
                                modelingEveryScore.push(new Score( null, exercise.id, exercise.title, 0));
                                modelingScoreString += '0,';                                
                                break;
                            default: break;
                        }
                    }
                });
            }

            // this.allQuizExercises.forEach((quiz) => {
            //     let bool : Boolean = true;
            //     // refactoring done changed 4 instances of exID to something more readable
            //     student.scoreListForQuizzes.forEach ((scoresQ) => {  // matching
            //         if (quiz.id === scoresQ.exID) {
            //             bool = false; // ensure to only enter the loop later once
            //             quizScoreString += scoresQ.absoluteScore + ',';
            //             quizEveryScore.push( new Score( scoresQ.resCompletionDate, scoresQ.exID, scoresQ.exTitle, +scoresQ.absoluteScore));
            //         }
            //     });
            //     if (bool) {
            //         quizEveryScore.push(new Score( null, quiz.id, quiz.title, 0));
            //         quizScoreString += '0,';
            //     }
            // });

            // this.allModelingExercises.forEach( (modelings) => { // adding up our score strings for our export
            //     let bool : Boolean = true;
            //     student.scoreListForModeling.forEach( (scoresM) => {
            //         if (modelings.id === scoresM.exID) {
            //             bool = false;
            //             modelingScoreString += scoresM.absoluteScore + ',';
            //             modelingEveryScore.push(new Score( scoresM.resCompletionDate, scoresM.exID, scoresM.exTitle, +scoresM.absoluteScore));
            //         }
            //     });
            //     if (bool) {
            //         modelingEveryScore.push(new Score( null, modelings.id, modelings.title, 0));
            //         modelingScoreString += '0,';
            //     }
            // });

            // this.allProgrammingExercises.forEach((programmings) => {
            //     let bool: Boolean = true;

            //     student.scoreListForProgramming.forEach( (scoresP) => {
            //         if (programmings.id === scoresP.exID) {
            //             bool = false;
            //             programmingScoreString += scoresP.absoluteScore + ',';
            //             programmingEveryScore.push( new Score( scoresP.resCompletionDate, scoresP.exID, scoresP.exTitle, +scoresP.absoluteScore));
            //         }
            //     });
            //     if (bool) {
            //         programmingEveryScore.push(new Score( null, programmings.id, programmings.title, 0));
            //         programmingScoreString += '0,';
            //     }
            // });

            // adding temporary variables to our final scores array list
            this.studentArray[indexStudent].totalScoreQuizzes = student.totalScoreQuizzes;
            this.studentArray[indexStudent].everyScoreForQuizzes = quizEveryScore;
            this.studentArray[indexStudent].everyScoreStringForQuizzes = quizScoreString;
            
            this.studentArray[indexStudent].totalScoreModeling = student.totalScoreModeling;
            this.studentArray[indexStudent].everyScoreForModeling = modelingEveryScore;
            this.studentArray[indexStudent].everyScoreStringForModeling = modelingScoreString;
            
            this.studentArray[indexStudent].totalScoreProgramming = student.totalScoreProgramming;
            this.studentArray[indexStudent].everyScoreForProgramming = programmingEveryScore;
            this.studentArray[indexStudent].everyScoreStringForProgramming = programmingScoreString;
        });

        // gets all students that were not caught in the results list
        // this.participations.forEach((participation) => {

        //     let modelingString = ''; // we define these strings to cover the calues needed in the export
        //     let quizString = '';
        //     let programmingString = '';
        //     let quizEveryScore: Array<Score>=[];
        //     let modelingEveryScore: Array<Score>=[];
        //     let programmingEveryScore: Array<Score>=[];

        //     const stud = participation.student;

        //     let student = new Student (stud.firstName, stud.lastName, stud.id, stud.login, stud.email, 0, 0,0,new Map<ExerciseTypes, Array<Score>>([]),new Map<ExerciseTypes, number>(),[], [],'', [],
        //         [],'', [], [],'',
        //         0, 0,0,0,0, 0,0,0,
        //         0,0,0,0,0,0,0,0,
        //         true,0);

        //     // create a 0 points entry if the student has no exercise results 
        //     if (!this.studentArray.some(student => student['id'] === student.id)) {

        //         /*   console.log('do we need participation score?');
        //            console.log(student.firstName);
        //            console.log(student.id); */ // TODO: not needed in completion of project check usefulness and delete

        //         this.allModelingExercises.forEach( (modelingExercise) => { // creating objects to store information about the given exercise
        //             modelingString += '0,';
        //             modelingEveryScore.push( new Score( null, modelingExercise.id, modelingExercise.title, 0));
        //         });
        //         this.allQuizExercises.forEach( (quizExercise) => {
        //             quizString += '0,';
        //             quizEveryScore.push(new Score( null, quizExercise.id, quizExercise.title, 0));
        //         });
        //         this.allProgrammingExercises.forEach( (programmingExercise) => {
        //             programmingString += '0,';
        //             programmingEveryScore.push(new Score( null, programmingExercise.id, programmingExercise.title, 0));
        //         });

        //         student.everyScoreForQuizzes = quizEveryScore;
        //         student.everyScoreForProgramming = programmingEveryScore;
        //         student.everyScoreForModeling = modelingEveryScore;
        //         student.everyScoreStringForQuizzes = quizString;
        //         student.everyScoreStringForProgramming = programmingString;
        //         student.everyScoreStringForModeling = modelingString;
        //         student.successfullyCompletedInPercent = 0;
        //         student.participationInPercent = 0;

        //         this.studentArray.push(student);

        //     } else {
        //         let indexOverallFinalScores : number = this.studentArray.findIndex( student => student.id === student.id);
        //         this.studentArray[indexOverallFinalScores].overallScore = this.studentArray[indexOverallFinalScores].totalScoreQuizzes
        //             + this.studentArray[indexOverallFinalScores].totalScoreProgramming + this.studentArray[indexOverallFinalScores].totalScoreModeling;
        //         }
        // });

        this.exportReady = true;
    }

    exportResults() { // method for exporting the csv with the needed data

        this.getAllScoresForAllCourseParticipants();

        if (this.exportReady && this.studentArray.length > 0) {
            const rows = [];
            this.studentArray.forEach((result, index) => {

                const firstName = result.firstName.trim();
                const lastName = result.lastName.trim();
                const studentId = result.login.trim();
                const email = result.email.trim();
                const quiz = result.totalScoreQuizzes;
                const programming = result.totalScoreProgramming;
                const modeling = result.totalScoreModeling;
                const score = result.overallScore;
                const quizString = result.everyScoreStringForQuizzes;
                const modelingString = result.everyScoreStringForModeling;
                const programmingString = result.everyScoreStringForProgramming;
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

    round(value, decimals): Number { // TODO check if useful and remove if not useable - already in use
        return Number(Math.round(Number(value + 'e' + decimals)) + 'e-' + decimals);
    }

    roundWithPower(number, precision): Number {// TODO check if useful and remove if not useable
        const factor = Math.pow(10, precision);
        const tempNumber = number * factor;
        const roundedTempNumber = Math.round(tempNumber);
        return roundedTempNumber / factor;
    }

    // Updated to Maps - pls check
    getTitlesMaxScoresAndAllQuizModelingProgrammingExercises(exercise: Exercise) { // calculating max score and title
        this.allExercises[exercise.type].push(exercise);
        
        switch (exercise.type) {
            case 'quiz':
                this.maxScoreForQuizzes += exercise.maxScore;
                // this.allQuizExercises.push(exercise);
                this.titleQuizString += exercise.title + ',';
                break;

            case 'programming-exercise':
                this.maxScoreForProgramming += exercise.maxScore;
                // this.allProgrammingExercises.push(exercise);
                this.titleProgrammingString += exercise.title + ',';
                break;

            case 'modeling-exercise':
                this.maxScoreForModeling += exercise.maxScore;
                // this.allModelingExercises.push(exercise);
                this.titleModelingString += exercise.title + ',';
                break;
            default:
        }
    }

    // Updated to Maps - pls check
    getScoresForQuizzesModelingProgrammingExercises(student: Student, exercise: Exercise, result) {

        let resultCompletionDate : Date = new Date(result.completionDate);
        let dueDate : Date = new Date(exercise.dueDate);

        // filter exercises (quiz filter || programming-exercise filter || modelling-exercise filer)
        if (result.rated === true || (result.rated == null && resultCompletionDate.getTime() <= dueDate.getTime()) || (exercise.type === 'modeling-exercise')){
            let indexStudent : number = this.studentArray.findIndex( stud => stud.id === student.id);
            if(indexStudent >= 0) {
                if(exercise.type === 'quiz'){
                    // TODO do we need the participation rate / quote ?
                    this.studentArray[indexStudent].participated++;
                    this.studentArray[indexStudent].participatedQuizzes++;
                    if (result.successful) {
                        this.studentArray[indexStudent].successful++;
                        this.studentArray[indexStudent].successfulQuizzes++;
                    }
                    this.studentArray[indexStudent].allExercises[exercise.type].push(new Score( resultCompletionDate, exercise.id, exercise.title, this.round((result.score * exercise.maxScore) / 100, 2)));
                } else {
                    let indexExc: number = this.studentArray[indexStudent].allExercises[exercise.type].findIndex(exc => exc.exID === exercise.id);
                    if(indexExc >= 0) { //if the exercise exist in the array
                        
                        let existingScore = this.studentArray[indexStudent].allExercises[exercise.type][indexExc];

                        if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                            if (this.studentArray[indexStudent].exerciseNotCounted) {   // TODO do we need the participation rate / quote ?
                                this.studentArray[indexStudent].participated++;
                                this.studentArray[indexStudent].participatedProgramming++;
                                this.studentArray[indexStudent].exerciseNotCounted = false;
                            }
                            if (result.successful) {    // TODO do we need the successful count ?
                                this.studentArray[indexStudent].successful++;
                                this.studentArray[indexStudent].successfulProgramming++;
                            }
                            // update entry with the data of the latest known exercise
                            this.studentArray[indexStudent].allExercises[exercise.type][indexExc] = {
                                'resCompletionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                            };
                        }
                    } else { //if the exercise does not exist in the array yet
                        if (this.studentArray[indexStudent].exerciseNotCounted) {
                            this.studentArray[indexStudent].participated++;
                            this.studentArray[indexStudent].participatedProgramming++;
                            this.studentArray[indexStudent].exerciseNotCounted = false;
                        }
                        if (result.successful) {
                            this.studentArray[indexStudent].successful++;
                            this.studentArray[indexStudent].successfulProgramming++;
                        }
                        this.studentArray[indexStudent].allExercises[exercise.type].push(new Score( resultCompletionDate, exercise.id, exercise.title, this.round((result.score * exercise.maxScore) / 100, 2)));
                    }
                }
            }
        }

        switch (exercise.type) {
            case 'quiz':
                if (result.rated === true) {   // There should only be 1 rated result
                    
                    let index : number = this.studentArray.findIndex( score => score.id === student.id);
                    this.studentArray[index].participated++;
                    this.studentArray[index].participatedQuizzes++;
                    if (result.successful) {
                        this.studentArray[index].successful++;
                        this.studentArray[index].successfulQuizzes++;
                    }
            
                    this.studentArray[index].scoreListForQuizzes.push(new Score( resultCompletionDate, exercise.id, exercise.title, this.round((result.score * exercise.maxScore) / 100, 2)));
                }
                break;

            case 'programming-exercise':
                if (resultCompletionDate.getTime() <= dueDate.getTime()) {
                    let indexStudent : number = this.studentArray.findIndex( score => score.id === student.id);
                    if(indexStudent >= 0) {
                        let indexExc: number = this.studentArray[indexStudent].scoreListForProgramming.findIndex(exc => exc.exID === exercise.id);
                        if(indexExc >= 0) { //if the exercise exist in the array
                            
                            let existingScore = this.studentArray[indexStudent].scoreListForProgramming[indexExc];

                            if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                                if (this.studentArray[indexStudent].exerciseNotCounted) {
                                    this.studentArray[indexStudent].participated++;
                                    this.studentArray[indexStudent].participatedProgramming++;
                                    this.studentArray[indexStudent].exerciseNotCounted = false;
                                }
                                if (result.successful) {
                                    this.studentArray[indexStudent].successful++;
                                    this.studentArray[indexStudent].successfulProgramming++;
                                }
                                this.studentArray[indexStudent].scoreListForProgramming[indexExc] = {
                                    'resCompletionDate': resultCompletionDate,
                                    'exID': exercise.id,
                                    'exTitle': exercise.title,
                                    'absoluteScore': this.round((result.score * exercise.maxScore) / 100, 2)
                                };
                            }
                        } else { //if the exercise does not exist in the array yet
                            // TODO Check if rules are fitting to logic
                            if (this.studentArray[indexStudent].exerciseNotCounted) {
                                this.studentArray[indexStudent].participated++;
                                this.studentArray[indexStudent].participatedProgramming++;
                                this.studentArray[indexStudent].exerciseNotCounted = false;
                            }
                            if (result.successful) {
                                this.studentArray[indexStudent].successful++;
                                this.studentArray[indexStudent].successfulProgramming++;
                            }
                            this.studentArray[indexStudent].scoreListForProgramming.push( new Score( resultCompletionDate, exercise.id, exercise.title, (this.round((result.score * exercise.maxScore) / 100, 2))));
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
                                this.studentArray[indexStudent].participatedModeling++;
                                this.studentArray[indexStudent].exerciseNotCounted = false;
                            }

                            if (result.successful) {
                                this.studentArray[indexStudent].successful++;
                                this.studentArray[indexStudent].successfulModeling++;
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
                            this.studentArray[indexStudent].participatedModeling++;
                            this.studentArray[indexStudent].exerciseNotCounted = false;
                        }

                        if (result.successful) {
                            this.studentArray[indexStudent].successful++;
                            this.studentArray[indexStudent].successfulModeling++;
                        }

                        this.studentArray[indexStudent].scoreListForModeling.push(new Score(resultCompletionDate, exercise.id, exercise.title, (this.round((result.score * exercise.maxScore) / 100, 2))));
                    }
                }
                // }

                break;
            default:
        }
    }

    // Updated to Maps - pls check
    getTotalScoresForQuizzesModelingProgrammingExercises() {

        // calculate the total scores for each student
        this.studentArray.forEach ( (student) => {
            // let totalScoreQuizzes : number = 0;
            // let totalScoreProgramming : number = 0;
            // let totalScoreModeling : number = 0;

            for ( let exType in ExerciseTypes ){
                student.allExercises[exType].forEach((excercise) => {
                    student.totalScores[exType] += +excercise.absoluteScore;
                });
            }

            // student.scoreListForQuizzes.forEach( (quiz) => {
            //     totalScoreQuizzes += +quiz.absoluteScore; // TODO test if the "unary plus" in front of studentsQuizScore is achieving the goal (should fix an Addition with Number problem)
            // });
            // student.totalScoreQuizzes = totalScoreQuizzes;

            // student.scoreListForModeling.forEach( (modeling) => {
            //     totalScoreModeling += +modeling.absoluteScore; // TODO test if the "unary plus" in front of studentsModelingScore is achieving the goal
            // });
            // student.totalScoreModeling = totalScoreModeling;

            // student.scoreListForProgramming.forEach( (programming) => {
            //     totalScoreProgramming += +programming.absoluteScore; // TODO test if the "unary plus" in front of studentsProgrammingScore is achieving the goal
            // });
            // student.totalScoreProgramming = totalScoreProgramming;
        });
    }

    callback() {
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

class Exercise { // creating a class for exercises
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

class Score {
    resCompletionDate: Date;
    exID: number; 
    exTitle: string; 
    absoluteScore: Number;

    constructor(resultCompletionDate: Date, exerciseID: number, exerciseTitle: string, absolutScore: Number){
        this.resCompletionDate = resultCompletionDate;
        this.exID = exerciseID;
        this.exTitle = exerciseTitle;
        this.absoluteScore = absolutScore;
    }
}

class Student { // creating a class for students for better code quality
    firstName: string;
    lastName: string;
    id: string;
    login: string;
    email: string;

    allExercises: Map<ExerciseTypes, Array<Score>>;

    totalScores: Map<ExerciseTypes, number>;

    // TODO check if needed
    totalScoreQuizzes: number; //needed
    totalScoreProgramming: number; //needed
    totalScoreModeling: number; //needed

    scoreListForQuizzes: Array<Score>;
    everyScoreForQuizzes: Array<Score>;
    everyScoreStringForQuizzes: string;

    scoreListForProgramming: Array<Score>;
    everyScoreForProgramming: Array<Score>;
    everyScoreStringForProgramming: string;

    scoreListForModeling: Array<Score>;
    everyScoreForModeling: Array<Score>;
    everyScoreStringForModeling: string;

    participated: number;
    participatedQuizzes: number;
    participatedProgramming: number;
    participatedModeling: number;

    // participationInPercent: number; // not needed - calculated in frontend
    participationQuizzesInPercent: number;
    participationProgrammingInPercent: number;
    participationModelingInPercent: number;


    successful: number;
    successfulQuizzes: number;
    successfulProgramming: number;
    successfulModeling: number;

    //successfullyCompletedInPercent: number; // not needed - calculated in frontend
    successfullyCompletedQuizzesInPercent: number;
    successfullyCompletedProgrammingInPercent: number;
    successfullyCompletedModelingInPercent: number;

    exerciseNotCounted: boolean;
    overallScore: number; // needed

    constructor(firstName: string, lastName: string, id: string, login: string, email: string,
                totalScoreQuizzes: number, totalScoreProgramming: number, totalScoreModeling: number,

                allExercises: Map<ExerciseTypes, Array<Score>>,

                totalScores: Map<ExerciseTypes, number>,

                scoreListForQuizzes: Array<Score>,
                everyScoreForQuizzes: Array<Score>,
                everyScoreStringForQuizzes: string,

                scoreListForProgramming: Array<Score>,
                everyScoreForProgramming: Array<Score>,
                everyScoreStringForProgramming: string,

                scoreListForModeling: Array<Score>,
                everyScoreForModeling: Array<Score>,
                everyScoreStringForModeling: string,


                participated: number, participatedQuizzes: number, participatedProgramming: number, participatedModeling: number,
                //participationInPercent: number, 
                participationQuizzesInPercent: number, participationProgrammingInPercent: number,
                participationModelingInPercent: number,

                successful: number, successfulQuizzes:number, successfulProgramming: number, successfulModeling: number,
                //successfullyCompletedInPercent: number, 
                successfullyCompletedQuizzesInPercent: number, successfullyCompletedProgrammingInPercent: number,
                successfullyCompletedModelingInPercent: number,

                exerciseNotCounted: boolean, overallScore: number)
    {

        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.login = login;
        this.email = email;

        this.allExercises = allExercises;

        this.totalScores = totalScores;

        this.totalScoreQuizzes = totalScoreQuizzes;
        this.totalScoreProgramming = totalScoreProgramming;
        this.totalScoreModeling = totalScoreModeling;

        this.scoreListForQuizzes = scoreListForQuizzes;
        this.everyScoreForQuizzes = everyScoreForQuizzes;
        this.everyScoreStringForQuizzes = everyScoreStringForQuizzes;

        this.scoreListForProgramming = scoreListForProgramming;
        this.everyScoreForProgramming = everyScoreForProgramming;
        this.everyScoreStringForProgramming = everyScoreStringForProgramming;

        this.scoreListForModeling = scoreListForModeling;
        this.everyScoreForModeling = everyScoreForModeling;
        this.everyScoreStringForModeling = everyScoreStringForModeling;

        this.participated = participated;
        this.participatedQuizzes = participatedQuizzes;
        this.participatedProgramming = participatedProgramming;
        this.participatedModeling = participatedModeling;

        //this.participationInPercent = participationInPercent;
        this.participationQuizzesInPercent = participationQuizzesInPercent;
        this.participationProgrammingInPercent = participationProgrammingInPercent;
        this.participationModelingInPercent = participationModelingInPercent;

        this.successful = successful;
        this.successfulQuizzes = successfulQuizzes;
        this.successfulProgramming = successfulProgramming;
        this.successfulModeling = successfulModeling;

        //this.successfullyCompletedInPercent = successfullyCompletedInPercent;
        this.successfullyCompletedQuizzesInPercent = successfullyCompletedQuizzesInPercent;
        this.successfullyCompletedProgrammingInPercent = successfullyCompletedProgrammingInPercent;
        this.successfullyCompletedModelingInPercent = successfullyCompletedModelingInPercent;

        this.exerciseNotCounted = exerciseNotCounted;
        this.overallScore = overallScore;
    }
}
