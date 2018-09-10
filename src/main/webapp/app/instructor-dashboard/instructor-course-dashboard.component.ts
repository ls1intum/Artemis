import {JhiAlertService} from 'ng-jhipster';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from 'rxjs/Subscription';
import {ActivatedRoute} from '@angular/router';
import {
    Course,
    CourseService
} from '../entities/course';
import { ExerciseType, ExerciseService } from '../entities/exercise';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [
        JhiAlertService
    ]
})

export class InstructorCourseDashboardComponent implements OnInit, OnDestroy { //initializing needed variables
    course: Course;
    paramSub: Subscription;
    predicate: any;
    reverse: any;
    results = [];
    exerciseTitles: Map<ExerciseType, string> = new Map<ExerciseType, string>();
    exerciseMaxScores: Map<ExerciseType, number> = new Map<ExerciseType, number>();
    allExercises: Map<string, Array<Exercise>> = new Map<string, Array<Exercise>>();
    exerciseCall = [];
    studentArray: Array<Student> = [];
    exportReady: Boolean = false;

    constructor(private route: ActivatedRoute,
                private courseService: CourseService, private exerciseService: ExerciseService) {
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
        /*this.courseService.findAllResults(courseId).subscribe(res => { // TODO Change call - this call gets all information to the results in the exercises
           this.results = res;
            this.groupResults();
        });*/
        this.results = [{id:'58731',first_name:'Stephan Krusche', last_name:'', login:'ne23kow', email: 'testmail@test.com', studentID: '108', exerciseTitle:'Quiz 20c', exerciseID: '265', score:'17', max_score:'6', completion_date: '2018-07-11 10:54:53', due_date: null, discriminator: 'Quiz', rated: true}];

        this.exerciseService.findAllExercisesByCourseId(courseId).subscribe(res => { // this call gets all exercise information for the course
            this.exerciseCall = res.body;

            console.log(this.exerciseCall);

            this.groupResults();
        });
    }

    groupResults() {

        if (!this.results || this.results.length === 0 || !this.exerciseCall || this.exerciseCall.length === 0) {
            return;
        }

        for(const exerciseType in ExerciseType){
            this.allExercises.set(exerciseType, []);
        }

        // iterating through the exercises result of the course
        this.exerciseCall.forEach( ex => {

            // create exercise object
            const exercise: Exercise = new Exercise(ex.id, ex.title, ex.maxScore, ex.type, ex.dueDate);

            console.log(this.allExercises);
            // create a list of all exercises
            const temp = this.allExercises.get(exercise.type);
            if (!temp.some( exc => exc['id'] === exercise.id)) { // make sure the exercise does not exist yet
                this.extractExerciseInformation(exercise);
            }
        });

        this.createStudents();
    }

    createStudents() { // creates students and initializes the result processing

        if (!this.results || this.results.length === 0 || !this.exerciseCall || this.exerciseCall.length === 0) {
            return;
        } // filtering

        // iterating through the students exercise results
        this.results.forEach( result => {

            // create a new student object to save the information in
            const student = new Student (result.first_name, result.last_name, result.id, result.login, result.email, new Map<ExerciseType, Array<Score>>([]),new Map<ExerciseType, number>(),new Map<ExerciseType, {successful:number, participated:number}>(), new Map<ExerciseType, Array<Score>>([]) , new Map<ExerciseType, string>(),
                0,0,true,0);

            const exercise: Exercise = new Exercise (result.exerciseID, result.exerciseTitle, result.max_score, result.discriminator, result.due_date);

            if(!this.studentArray.some(stud => stud['id'] === student.id)) {
                this.studentArray.push(student);
            }

            this.getScoresForExercises(student, exercise, result);

        });

        this.getTotalScores();

        this.createScoreString();
    }

    createScoreString () { // create a score string for each student and add the exercise (even if 0 points) to the everyScore Map

        this.studentArray.forEach((student, indexStudent) => {

            // check for all exercise types if we have exercises in the course
            if(ExerciseType){
                for ( const exType in ExerciseType ){

                    // check if the student participated in the exercises of the course and get the scores
                    this.allExercises[exType].forEach( exercise => {
                        let bool: Boolean = true;

                        // iterate through all the participated exercises by the student
                        student.allExercises[exType].forEach( score => {
                            if (exercise.id === score.exerciseID) {
                                bool = false; // ensure to only enter the loop later once
                                this.studentArray[indexStudent].everyScoreString[exercise.type] += score.absoluteScore + ',';
                                this.studentArray[indexStudent].everyScore[exercise.type].push(score);
                            }
                        });

                        // if the student did not participate in the exercise, a zero points score is generated
                        if (bool) {
                            this.studentArray[indexStudent].everyScoreString[exercise.type] += '0,';
                            this.studentArray[indexStudent].everyScore[exercise.type].push(new Score(null, exercise.id, exercise.title, 0));
                        }
                    });
                }
            }
        });

        this.exportReady = true;
    }

    extractExerciseInformation(exercise: Exercise) {
        // extracting max score and title for each exercise in the course
        let exArr: Exercise[] = this.allExercises.get(exercise.type);
        exArr.push(exercise);
        this.allExercises.set(exercise.type , exArr);
        // this.allExercises[exercise.type].push(exercise);
        this.exerciseTitles[exercise.type] += exercise.title + ',';
        this.exerciseMaxScores[exercise.type] += exercise.maxScore;
    }

    getScoresForExercises(student: Student, exercise: Exercise, result) {

        const resultCompletionDate: Date = new Date(result.completion_date);
        const dueDate: Date = new Date(exercise.dueDate);

        // filter if exercise result is relevant (quiz filter || programming-exercise filter || modelling-exercise filer)
        if (result.rated === true || (result.rated == null && resultCompletionDate.getTime() <= dueDate.getTime())){
            const indexStudent: number = this.studentArray.findIndex( stud => stud.id === student.id);

            if(indexStudent >= 0) { // check if the student exists in our array
                // quiz exercises only have one rated result
                if(exercise.type === 'quiz'){
                    this.studentArray[indexStudent].participated++;
                    this.studentArray[indexStudent].successAndParticipationExercises[exercise.type].participated++;
                    if (result.successful) {
                        this.studentArray[indexStudent].successful++;
                        this.studentArray[indexStudent].successAndParticipationExercises[exercise.type].successful++;
                    }
                    this.studentArray[indexStudent].allExercises[exercise.type].push(new Score( resultCompletionDate, exercise.id, exercise.title, this.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)));
                } else {
                    const indexExc: number = this.studentArray[indexStudent].allExercises[exercise.type].findIndex(exc => exc.exerciseID === exercise.id);

                    if (this.studentArray[indexStudent].exerciseNotCounted) {
                        this.studentArray[indexStudent].participated++;
                        this.studentArray[indexStudent].successAndParticipationExercises[exercise.type].participated++;
                        this.studentArray[indexStudent].exerciseNotCounted = false;
                    }
                    if (result.successful) {
                        this.studentArray[indexStudent].successful++;
                        this.studentArray[indexStudent].successAndParticipationExercises[exercise.type].successful++;
                    }

                    if(indexExc >= 0) { // if the exercise score exist in the array

                        const existingScore = this.studentArray[indexStudent].allExercises[exercise.type][indexExc];

                        // we want to have the last result withing the due date (see above)
                        if (resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {
                             // update entry with the data of the latest known exercise
                            this.studentArray[indexStudent].allExercises[exercise.type][indexExc] = {
                                'resCompletionDate': resultCompletionDate,
                                'exerciseID': exercise.id,
                                'exerciseTitle': exercise.title,
                                'absoluteScore': this.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)
                            };
                        }
                    } else { // if the exercise score does not exist in the array yet we add it as a new Score
                        this.studentArray[indexStudent].allExercises[exercise.type].push(new Score( resultCompletionDate, exercise.id, exercise.title, this.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)));
                    }
                }
            }
        }
    }

    getTotalScores() {
        // calculate the total scores for each student
        this.studentArray.forEach ( student => {
            if(ExerciseType){
                for ( const exType in ExerciseType ){
                    student.allExercises[exType].forEach( excercise => {
                        student.totalScores[exType] += +excercise.absoluteScore;
                    });
                }
            }
        });
    }

    exportResults() { // method for exporting the csv with the needed data

        this.createStudents();

        if (this.exportReady && this.studentArray.length > 0) {
            const rows = [];
            this.studentArray.forEach( (student, index) => {

                const firstName = student.firstName.trim();
                const lastName = student.lastName.trim();
                const studentId = student.login.trim();
                const email = student.email.trim();
                const quizTotal = student.totalScores['quiz'];
                const programmingTotal = student.totalScores['programming'];
                const modelingTotal = student.totalScores['modeling'];
                const score = student.overallScore;
                const quizString = student.everyScoreString['quiz'];
                const modelingString = student.everyScoreString['modeling'];
                const programmingString = student.everyScoreString['programming'];
                if (index === 0) {
                    const info = 'data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,QuizTotalScore,'; // shortening line length and complexity
                    rows.push(info + this.exerciseTitles['quiz'] + 'ProgrammingTotalScore,' + this.exerciseTitles['programming'] + 'ModelingTotalScore,' + this.exerciseTitles['modeling'] + 'OverallScore');
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quizTotal + ',' + quizString + '' + programmingTotal + ',' + programmingString + '' + modelingTotal + ',' + modelingString + '' + score);
                } else {
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quizTotal + ',' + quizString + '' + programmingTotal + ',' + programmingString + '' + modelingTotal + ',' + modelingString + '' + score);
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
        }
    }

    round(value, decimals): Number { // TODO find better one
        return Number(Math.round(Number(value + 'e' + decimals)) + 'e-' + decimals);
    }

    /**
     * Decimal adjustment of a number.
     *
     * @param   {Number}    value   The number.
     * @param   {Integer}   exp     The exponent (the 10 logarithm of the adjustment base).
     * @returns {Number}            The adjusted value.
     */
    roundLikeMozilla(value, exp) {
        // If the exp is undefined or zero...
        if (typeof exp === 'undefined' || +exp === 0) {
            return Math['round'](value);
        }
        value = +value;
        exp = +exp;
        // If the value is not a number or the exp is not an integer...
        if (isNaN(value) || !(typeof exp === 'number' && exp % 1 === 0)) {
            return NaN;
        }
        // Shift
        value = value.toString().split('e');
        value = Math['round'](+(value[0] + 'e' + (value[1] ? (+value[1] - exp) : -exp)));
        // Shift back
        value = value.toString().split('e');
        return +(value[0] + 'e' + (value[1] ? (+value[1] + exp) : exp));
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
    exerciseID: number;
    exerciseTitle: string;
    absoluteScore: Number;

    constructor(resultCompletionDate: Date, exerciseID: number, exerciseTitle: string, absolutScore: Number){
        this.resCompletionDate = resultCompletionDate;
        this.exerciseID = exerciseID;
        this.exerciseTitle = exerciseTitle;
        this.absoluteScore = absolutScore;
    }
}

class Student { // creating a class for students for better code quality
    firstName: string;
    lastName: string;
    id: string;
    login: string;
    email: string;
    allExercises: Map<ExerciseType, Array<Score>>;
    totalScores: Map<ExerciseType, number>;
    successAndParticipationExercises: Map<ExerciseType, {successful: number, participated: number}>;
    everyScore: Map<ExerciseType, Array<Score>>;
    everyScoreString: Map<ExerciseType, string>;
    participated: number;
    successful: number;
    exerciseNotCounted: boolean;
    overallScore: number;

    constructor(firstName: string,
                lastName: string,
                id: string,
                login: string,
                email: string,
                allExercises: Map<ExerciseType, Array<Score>>,
                totalScores: Map<ExerciseType, number>,
                successAndParticipationExercises: Map<ExerciseType, {successful: number, participated: number}>,
                everyScore: Map<ExerciseType, Array<Score>>,
                everyScoreString: Map<ExerciseType, string>,
                participated: number,
                successful: number,
                exerciseNotCounted: boolean,
                overallScore: number
                ){
        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.login = login;
        this.email = email;
        this.allExercises = allExercises;
        this.totalScores = totalScores;
        this.successAndParticipationExercises = successAndParticipationExercises;
        this.everyScore = everyScore;
        this.everyScoreString = everyScoreString;
        this.participated = participated;
        this.successful = successful;
        this.exerciseNotCounted = exerciseNotCounted;
        this.overallScore = overallScore;
    }
}
