import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseExerciseService, CourseService } from '../entities/course';
import { Exercise, ExerciseType } from '../entities/exercise';
import { Result } from 'app/entities/result';
import { Moment } from 'moment';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    providers: [JhiAlertService]
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    course: Course;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    results: Result[] = [];
    exercises: Exercise[] = [];
    exerciseTitles = new Map<string, string>();
    exerciseMaxScores = new Map<string, number>();
    allExercises = new Map<string, Exercise[]>();
    studentArray: Array<Student> = [];
    exportReady: Boolean = false;

    constructor(private route: ActivatedRoute, private courseService: CourseService, private courseExerciseService: CourseExerciseService) {
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
        this.courseService.findAllResults(courseId).subscribe(res => {
            // this.results = res;
            this.groupResultsAfterExerciseTypes();
        });

        this.courseExerciseService.findAllExercises(courseId).subscribe(res => {
            // this call gets all exercise information for the course
            this.exercises = res.body;
            this.groupResultsAfterExerciseTypes();
        });
    }

    groupResultsAfterExerciseTypes() {
        if (!this.results || this.results.length === 0 || !this.exercises || this.exercises.length === 0) {
            return;
        }

        for (const exerciseType in ExerciseType) {
            const exercisesWithType = this.exercises.filter(exercise => exercise.type === exerciseType);
            this.allExercises[exerciseType] = exercisesWithType;
            this.exerciseTitles[exerciseType] = exercisesWithType.map(exercise => exercise.title).join(', ');
            this.exerciseMaxScores[exerciseType] = exercisesWithType
                .map(exercise => exercise.maxScore)
                .reduce((total, num) => total + num, 0);
        }

        this.createStudents();
    }

    createStudents() {
        // creates students and initializes the result processing

        if (!this.results || this.results.length === 0 || !this.exercises || this.exercises.length === 0) {
            return;
        } // filtering

        // iterating through the students exercise results
        this.results.forEach(result => {
            if (result.participation && result.participation.student && result.participation.exercise) {
                // create a new student object to save the information in
                const student = new Student(
                    result.participation.student.firstName,
                    result.participation.student.lastName,
                    result.participation.student.id,
                    result.participation.student.login,
                    result.participation.student.email,
                    new Map<ExerciseType, Score[]>([]),
                    new Map<ExerciseType, number>(),
                    new Map<ExerciseType, { successful: number; participated: number }>(),
                    new Map<ExerciseType, Score[]>([]),
                    new Map<ExerciseType, string>(),
                    0,
                    0,
                    true,
                    0
                );

                const exercise = result.participation.exercise;

                if (!this.studentArray.some(stud => stud.id === student.id)) {
                    this.studentArray.push(student);
                    const indexStudent: number = this.studentArray.findIndex(stud => stud.id === student.id);
                    // generate empty maps for each student
                    for (const exerciseType in ExerciseType) {
                        this.studentArray[indexStudent].everyScoreString.set(ExerciseType[exerciseType], '');
                        this.studentArray[indexStudent].everyScore.set(ExerciseType[exerciseType], []);
                        this.studentArray[indexStudent].successAndParticipationExercises.set(ExerciseType[exerciseType], {
                            successful: 0,
                            participated: 0
                        });
                        this.studentArray[indexStudent].allExercises.set(ExerciseType[exerciseType], []);
                        this.studentArray[indexStudent].totalScores.set(ExerciseType[exerciseType], 0);
                    }
                }

                this.getScoresForExercises(student, exercise, result);
            }
        });

        this.getTotalScores();

        this.createScoreString();
    }

    createScoreString() {
        // create a score string for each student and add the exercise (even if 0 points) to the everyScore Map

        this.studentArray.forEach((student, indexStudent) => {
            // check for all exercise types if we have exercises in the course
            if (ExerciseType) {
                for (const exType in ExerciseType) {
                    // check if the student participated in the exercises of the course and get the scores
                    let excAll: Exercise[] = this.allExercises.get(ExerciseType[exType]);
                    excAll.forEach(exercise => {
                        let bool: Boolean = true;

                        // iterate through all the participated exercises by the student
                        let excStAll: Score[] = student.allExercises.get(ExerciseType[exType]);
                        excStAll.forEach(score => {
                            if (exercise.id === score.exerciseID) {
                                bool = false; // ensure to only enter the loop later once

                                let scoreStr: string = this.studentArray[indexStudent].everyScoreString.get(exercise.type);
                                scoreStr += score.absoluteScore + ',';
                                this.studentArray[indexStudent].everyScoreString.set(exercise.type, scoreStr);

                                let scoreArr: Score[] = this.studentArray[indexStudent].everyScore.get(exercise.type);
                                scoreArr.push(score);
                                this.studentArray[indexStudent].everyScore.set(exercise.type, scoreArr);

                                // this.studentArray[indexStudent].everyScoreString[exercise.type] += score.absoluteScore + ',';
                                // this.studentArray[indexStudent].everyScore[exercise.type].push(score);
                            }
                        });

                        // if the student did not participate in the exercise, a zero points score is generated
                        if (bool) {
                            let scoreStr: string = this.studentArray[indexStudent].everyScoreString.get(exercise.type);
                            scoreStr += '0,';
                            this.studentArray[indexStudent].everyScoreString.set(exercise.type, scoreStr);

                            let scoreArr: Score[] = this.studentArray[indexStudent].everyScore.get(exercise.type);
                            scoreArr.push(new Score(null, exercise.id, exercise.title, 0));
                            this.studentArray[indexStudent].everyScore.set(exercise.type, scoreArr);

                            // this.studentArray[indexStudent].everyScoreString[exercise.type] += '0,';
                            // this.studentArray[indexStudent].everyScore[exercise.type].push(new Score(null, exercise.id, exercise.title, 0));
                        }
                    });
                }
            }
        });

        this.exportReady = true;
    }

    getScoresForExercises(student: Student, exercise: Exercise, result: Result) {
        // filter if exercise result is relevant (quiz filter || programming-exercise filter || modelling-exercise filer)
        if (result.rated === true || (result.rated == null && result.completionDate <= exercise.dueDate)) {
            const indexStudent: number = this.studentArray.findIndex(stud => stud.id === student.id);

            if (indexStudent >= 0) {
                // check if the student exists in our array
                // quiz exercises only have one rated result
                if (exercise.type === 'quiz') {
                    this.studentArray[indexStudent].participated++;

                    let excSP: { successful: number; participated: number } = this.studentArray[
                        indexStudent
                    ].successAndParticipationExercises.get(exercise.type);
                    excSP.participated++;
                    if (result.successful) {
                        this.studentArray[indexStudent].successful++;
                        excSP.successful++;
                    }
                    this.studentArray[indexStudent].successAndParticipationExercises.set(exercise.type, excSP);

                    let excAll: Score[] = this.studentArray[indexStudent].allExercises.get(exercise.type);
                    excAll.push(
                        new Score(
                            result.completionDate,
                            exercise.id,
                            exercise.title,
                            CourseDashboardComponent.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)
                        )
                    );
                    this.studentArray[indexStudent].allExercises.set(exercise.type, excAll);
                } else {
                    let excAll: Score[] = this.studentArray[indexStudent].allExercises.get(exercise.type);
                    const indexExc: number = excAll.findIndex(exc => exc.exerciseID === exercise.id);
                    let excSP: { successful: number; participated: number } = this.studentArray[
                        indexStudent
                    ].successAndParticipationExercises.get(exercise.type);

                    if (this.studentArray[indexStudent].exerciseNotCounted) {
                        this.studentArray[indexStudent].participated++;
                        excSP.participated++;
                        this.studentArray[indexStudent].exerciseNotCounted = false;
                    }
                    if (result.successful) {
                        this.studentArray[indexStudent].successful++;
                        excSP.successful++;
                    }

                    this.studentArray[indexStudent].successAndParticipationExercises.set(exercise.type, excSP);

                    if (indexExc >= 0) {
                        // if the exercise score exist in the array

                        const existingScore = excAll[indexExc];

                        // we want to have the last result withing the due date (see above)
                        if (result.completionDate > existingScore.resCompletionDate) {
                            // update entry with the data of the latest known exercise
                            excAll[indexExc] = {
                                resCompletionDate: result.completionDate,
                                exerciseID: exercise.id,
                                exerciseTitle: exercise.title,
                                absoluteScore: CourseDashboardComponent.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)
                            };
                            this.studentArray[indexStudent].allExercises.set(exercise.type, excAll);
                        }
                    } else {
                        // if the exercise score does not exist in the array yet we add it as a new Score
                        excAll.push(
                            new Score(
                                result.completionDate,
                                exercise.id,
                                exercise.title,
                                CourseDashboardComponent.roundLikeMozilla((result.score * exercise.maxScore) / 100, -2)
                            )
                        );
                        this.studentArray[indexStudent].allExercises.set(exercise.type, excAll);
                    }
                }
            }
        }
    }

    getTotalScores() {
        // calculate the total scores for each student
        this.studentArray.forEach(student => {
            if (ExerciseType) {
                for (const exType in ExerciseType) {
                    let excAll: Score[] = student.allExercises.get(ExerciseType[exType]);
                    let totS: number = student.totalScores.get(ExerciseType[exType]);
                    excAll.forEach(excercise => {
                        totS += +excercise.absoluteScore;
                    });
                    student.totalScores.set(ExerciseType[exType], totS);
                }
                for (const exType in ExerciseType) {
                    student.overallScore += student.totalScores.get(ExerciseType[exType]);
                }
            }
        });
    }

    exportResults() {
        // method for exporting the csv with the needed data

        if (this.exportReady && this.studentArray.length > 0) {
            const rows: string[] = [];
            this.studentArray.forEach((student, index) => {
                const firstName = student.firstName.trim();
                const lastName = student.lastName.trim();
                const studentId = student.login.trim();
                const email = student.email.trim();
                const quizTotal = student.totalScores.get('quiz');
                const programmingTotal = student.totalScores.get('programming');
                const modelingTotal = student.totalScores.get('modeling');
                const score = quizTotal + programmingTotal + modelingTotal;
                const quizString = student.everyScoreString.get('quiz');
                const modelingString = student.everyScoreString.get('modeling');
                const programmingString = student.everyScoreString.get('programming');
                if (index === 0) {
                    const info = 'data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,QuizTotalScore,'; // shortening line length and complexity
                    rows.push(
                        info +
                            this.exerciseTitles.get('quiz') +
                            'ProgrammingTotalScore,' +
                            this.exerciseTitles.get('programming') +
                            'ModelingTotalScore,' +
                            this.exerciseTitles.get('modeling') +
                            'OverallScore'
                    );
                }
                rows.push(
                    firstName +
                        ',' +
                        lastName +
                        ',' +
                        studentId +
                        ',' +
                        email +
                        ',' +
                        quizTotal +
                        ',' +
                        quizString +
                        '' +
                        programmingTotal +
                        ',' +
                        programmingString +
                        '' +
                        modelingTotal +
                        ',' +
                        modelingString +
                        '' +
                        score
                );
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

    /**
     * Better rounding function
     *
     * @param value   The number.
     * @param exp     The exponent (the 10 logarithm of the adjustment base).
     * @returns       The adjusted value.
     */
    static roundLikeMozilla(value: any, exp: number) {
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
        value = Math['round'](+(value[0] + 'e' + (value[1] ? +value[1] - exp : -exp)));
        // Shift back
        value = value.toString().split('e');
        return +(value[0] + 'e' + (value[1] ? +value[1] + exp : exp));
    }

    callback() {}

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}

class Score {
    resCompletionDate: Moment;
    exerciseID: number;
    exerciseTitle: string;
    absoluteScore: Number;

    constructor(resultCompletionDate: Moment, exerciseID: number, exerciseTitle: string, absolutScore: Number) {
        this.resCompletionDate = resultCompletionDate;
        this.exerciseID = exerciseID;
        this.exerciseTitle = exerciseTitle;
        this.absoluteScore = absolutScore;
    }
}

class Student {
    constructor(
        public firstName: string,
        public lastName: string,
        public id: number,
        public login: string,
        public email: string,
        public allExercises: Map<string, Array<Score>>,
        public totalScores: Map<string, number>,
        public successAndParticipationExercises: Map<string, { successful: number; participated: number }>,
        public everyScore: Map<string, Array<Score>>,
        public everyScoreString: Map<string, string>,
        public participated: number,
        public successful: number,
        public exerciseNotCounted: boolean,
        public overallScore: number
    ) {}
}
