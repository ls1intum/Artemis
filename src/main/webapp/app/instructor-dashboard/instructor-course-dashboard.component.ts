import {JhiAlertService} from 'ng-jhipster';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from 'rxjs/Subscription';
import {ActivatedRoute} from '@angular/router';
import {
    Course,
    CourseParticipationService,
    CourseResultService,
    CourseScoresService,
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
    rows = [];
    results = [];
    participations = [];    //[Participation]
    courseScores = [];
    typeQ = [];
    typeP = [];
    typeM = [];
    allQuizExercises = [];
    allProgrammingExercises = [];
    allModellingExercises = [];
    titleQuizString = '';
    titleProgrammingString = '';
    titleModellingString = '';
    finalScores = [];
    exportReady = false;

    constructor(private route: ActivatedRoute,
                private courseService: CourseService,
                private courseResultService: CourseResultService,
                private courseParticipationService: CourseParticipationService,
                private courseScoresService: CourseScoresService) {
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
        this.courseResultService.findAll(courseId).subscribe(res => {
            this.results = res;
            this.groupResults();
        });

        this.courseParticipationService.findAll(courseId).subscribe(res => {
            this.participations = res;
            this.groupResults();
            //TODO: get rid of this call because all participations are probably stored in this.results anyway, so rather get them from there
        });

        this.courseScoresService.find(courseId).subscribe(res => {
            this.courseScores = res;
            this.groupResults();
            //TODO get rid of this call and refactor the html page
        });

    }

    groupResults() {

        if (!this.results || !this.participations || !this.courseScores || this.participations.length === 0 || this.results.length === 0 || this.courseScores.length === 0) {
            return;
        }

        const rows = {};
        const exercisesSeen = {};
        let maximalZuErreichendePunkteQ = 0;
        let maximalZuErreichendePunkteM = 0;
        let maximalZuErreichendePunkteP = 0;

        for (const participation of this.participations) {

            if (!rows[participation.student.id]) {
                rows[participation.student.id] = {
                    'firstName': participation.student.firstName,
                    'lastName': participation.student.lastName,
                    'login': participation.student.login,
                    'participated': 0,
                    'participationInPercent': 0,
                    'successful': 0,
                    'successfullyCompletedInPercent': 0,
                    'overallScore': 0,
                };
            }

            rows[participation.student.id].participated++;

            const exercise = participation.exercise;

            if (!exercisesSeen[exercise.id]) {
                exercisesSeen[exercise.id] = true;
                this.numberOfExercises++;


                switch (exercise.type) {
                    case "quiz":
                        maximalZuErreichendePunkteQ += exercise.maxScore;

                        this.allQuizExercises[exercise.id] = {
                            'exId': exercise.id,
                            'exTitle': exercise.title
                        };
                        this.titleQuizString += exercise.title + ',';

                        break;

                    case "programming-exercise":
                        maximalZuErreichendePunkteP += exercise.maxScore;

                        this.allProgrammingExercises[exercise.id] = {
                            'exId': exercise.id,
                            'exTitle': exercise.title
                        };
                        this.titleProgrammingString += exercise.title + ',';

                        break;
                    case "modeling-exercise":
                        maximalZuErreichendePunkteM += exercise.maxScore;

                        this.allModellingExercises[exercise.id] = {
                            'exId': exercise.id,
                            'exTitle': exercise.title
                        };
                        this.titleModellingString += exercise.title + ',';
                        break;

                    default:
                }

            }
        }

        // Successful Participations as the total amount and a relative value to all Exercises

        //TODO: If these values are not needed right away, move this functionality to the place where we check these conditions anyway
        for (const result of this.results) {

            if (result.successful) {
                if(result.participation.exercise.type === "quiz") {
                    if (result.rated === true) {
                        rows[result.participation.student.id].successful++;
                    }
                }
                else {
                    //TODO: also take into account that the last result before the due date has to be taken for programming exercise (see code below)
                    //TODO: also take into account that the last submission has to be taken for modeling exercises (see code below)
                    rows[result.participation.student.id].successful++;
                }
            }
        }

        for (const studentId in rows) {
            rows[studentId].successfullyCompletedInPercent = (rows[studentId].successful / this.numberOfExercises) * 100;
        }

        // Relative amount of participation in all exercises
        // Since 1 user can have multiple participations studentSeen makes sure each student is only calculated once
        const studentSeen = {};


        for (const participation of this.participations) {
            if (!studentSeen[participation.student.id]) {
                studentSeen[participation.student.id] = true;
                rows[participation.student.id].participationInPercent = (rows[participation.student.id].participated / this.numberOfExercises) * 100;
            }
        }

        for (const courseScore of this.courseScores) {
            if (courseScore.participation.student) {
                rows[courseScore.participation.student.id].overallScore = courseScore.score; //TODO: this line of code gives you not the overall score!!!! FDE
            }
        }

        //why the hell are we doing this???
        this.rows = Object.keys(rows).map(key => rows[key]);
    }

    getAllScoresForAllCourseParticipants() {

        if (!this.results || !this.participations || !this.courseScores || this.participations.length === 0 || this.results.length === 0 || this.courseScores.length === 0) {
            return;
        }

        const studentsQuizScores = {};
        const studentsProgrammingScores = {};
        const studentsModelingScores = {};

        for (const result of this.results) {

            const student = result.participation.student;
            const exercise = result.participation.exercise;

            if (!studentsProgrammingScores[student.id]) {
                studentsProgrammingScores[student.id] = {
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'programming-exercise',
                    'totalScore': 0,
                    'scoreListP': {},
                    'scoreListPString': ''
                };
            }
            if (!studentsQuizScores[student.id]) {
                studentsQuizScores[student.id] = {
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'quiz',
                    'totalScore': 0,
                    'scoreListQ': {},
                    'scoreListQString': ''
                };
            }
            if (!studentsModelingScores[student.id]) {
                studentsModelingScores[student.id] = {
                    'firstName': student.firstName,
                    'lastName': student.lastName,
                    'id': student.id,
                    'login': student.login,
                    'email': student.email,
                    'exType': 'modeling-exercise',
                    'totalScore': 0,
                    'scoreListM': {},
                    'scoreListMString': ''
                };
            }



            let resultCompletionDate = new Date(result.completionDate);
            let dueDate = new Date(exercise.dueDate);

            switch (exercise.type) {
                case "quiz":
                    if (result.rated === true) {   //There should only be 1 rated result

                        studentsQuizScores[student.id].scoreListQ[exercise.id] = {
                            'resCompletionDate': resultCompletionDate,
                            'exID': exercise.id,
                            'exTitle': exercise.title,
                            'absoluteScore': Math.round((result.score * exercise.maxScore) / 10) / 10 // divide afterwards to round to 2 decimal places
                        };
                    }
                    break;

                case "programming-exercise":
                    if (resultCompletionDate.getTime() <= dueDate.getTime()) {

                        const existingScore = studentsProgrammingScores[student.id].scoreListP[exercise.id];
                        if (existingScore == null || resultCompletionDate.getTime() > existingScore.resCompletionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                            studentsProgrammingScores[student.id].scoreListP[exercise.id] = {
                                'resCompletionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': Math.round((result.score * exercise.maxScore) / 10) / 10 // divide afterwards to round to 2 decimal places
                            };
                        }
                    }
                    break;

                case "modeling-exercise":
                    // we can also have results (due to the manual assessment) that appear after the completion date
                    // if (completionDate.getTime() <= dueDate.getTime()) {

                        const existingScore = studentsModelingScores[student.id].scoreListM[exercise.id];
                        if(existingScore == null || resultCompletionDate.getTime() > existingScore.resCompletionDate) {     // we want to have the last result
                            studentsModelingScores[student.id].scoreListM[exercise.id] = {
                                'resCompletionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': Math.round((result.score * exercise.maxScore) / 10) / 10 // divide afterwards to round to 2 decimal places
                            };
                        }
                    // }

                    break;
                default:
            }

        }



        //TODO: why the hell are we doing this?
        this.typeQ = Object.keys(studentsQuizScores).map(key => studentsQuizScores[key]);
        this.typeP = Object.keys(studentsQuizScores).map(key => studentsProgrammingScores[key]);
        this.typeM = Object.keys(studentsQuizScores).map(key => studentsModelingScores[key]);

        for(const studentsQuizScore of this.typeQ) {
            let totalScore = 0;
            for(const quizzes in studentsQuizScore.scoreListQ) {
                totalScore += studentsQuizScore.scoreListQ[quizzes].absoluteScore;
            }
            studentsQuizScore.totalScore = totalScore;
        }

        for(const studentsModelingScore of this.typeM) {
            let totalScore = 0;
            for(const modellings in studentsModelingScore.scoreListM) {
                totalScore += studentsModelingScore.scoreListM[modellings].absoluteScore;
            }
            studentsModelingScore.totalScore = totalScore;
        }

        for(const studentsProgrammingScore of this.typeP) {
            let totalScore = 0;
            for(const programmings in studentsProgrammingScore.scoreListP) {
                totalScore += studentsProgrammingScore.scoreListP[programmings].absoluteScore;
            }
            studentsProgrammingScore.totalScore = totalScore;
        }

        this.mergeScoresForExerciseCategories();
    }

    mergeScoresForExerciseCategories() {

        const finalScores = {};


        for (const studentsQuizScore of this.typeQ) {
            let stringQ = '';
            let qStringEveryScore = {};


            for (const quizzes in this.allQuizExercises) {
                let bool = true;
                let exId = this.allQuizExercises[quizzes].exId;

                for (const scoresQ in studentsQuizScore.scoreListQ) {
                    let exID = studentsQuizScore.scoreListQ[scoresQ].exID;
                    if (exId == exID) {
                        bool = false;
                        stringQ += studentsQuizScore.scoreListQ[scoresQ].absoluteScore + ',';
                        qStringEveryScore = {
                            'exID': exID, 'exTitle': this.allQuizExercises[quizzes].title,
                            'absoluteScore': studentsQuizScore.scoreListQ[scoresQ].absoluteScore
                        };
                    }
                }
                if (bool) {
                    qStringEveryScore = {
                        'exID': exId, 'exTitle': this.allQuizExercises[quizzes].title,
                        'absoluteScore': 0
                    };
                    stringQ += '0,';
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
                    'ModellingTotalScore': 0,
                    'ModellingEveryScore': {},
                    'ModellingScoreString': '',
                    'OverallScore': 0


                };
            }
            finalScores[studentsQuizScore.id].QuizTotalScore = studentsQuizScore.totalScore;
            finalScores[studentsQuizScore.id].QuizEveryScore = qStringEveryScore;
            finalScores[studentsQuizScore.id].QuizScoreString = stringQ;
        }

        for (const studentsModelingScore of this.typeM) {

            let stringM = '';
            let mStringEveryScore = {};

            for (const modellings in this.allModellingExercises) {
                let bool = true;
                let exId = this.allModellingExercises[modellings].exId;

                for (const scores in studentsModelingScore.scoreListM) {
                    let exID = studentsModelingScore.scoreListM[scores].exID;
                    if (exId == exID) {
                        bool = false;
                        stringM += studentsModelingScore.scoreListM[scores].absoluteScore + ',';
                        mStringEveryScore = {
                            'exID': exID, 'exTitle': this.allModellingExercises[modellings].title,
                            'absoluteScore': studentsModelingScore.scoreListM[scores].absoluteScore
                        };
                    }
                }
                if (bool) {
                    mStringEveryScore = {
                        'exID': exId, 'exTitle': this.allModellingExercises[modellings].title,
                        'absoluteScore': 0
                    };
                    stringM += '0,';
                }
            }


            finalScores[studentsModelingScore.id].ModellingTotalScore = studentsModelingScore.totalScore;
            finalScores[studentsModelingScore.id].ModellEveryScore = mStringEveryScore;
            finalScores[studentsModelingScore.id].ModellingScoreString = stringM;


        }


        for (const studentsProgrammingScore of this.typeP) {

            let stringP = '';
            let pStringEveryScore = {};

            for (const programmings in this.allProgrammingExercises) {
                let bool = true;
                let exId = this.allProgrammingExercises[programmings].exId;

                for (const scores in studentsProgrammingScore.scoreListP) {
                    let exID = studentsProgrammingScore.scoreListP[scores].exID;
                    if (exId == exID) {
                        bool = false;
                        stringP += studentsProgrammingScore.scoreListP[scores].absoluteScore + ',';
                        pStringEveryScore = {
                            'exID': exID, 'exTitle': this.allProgrammingExercises[programmings].title,
                            'absoluteScore': studentsProgrammingScore.scoreListP[scores].absoluteScore
                        };
                    }
                }
                if (bool) {
                    pStringEveryScore = {
                        'exID': exId, 'exTitle': this.allProgrammingExercises[programmings].title,
                        'absoluteScore': 0
                    };
                    stringP += '0,';
                }
            }

            finalScores[studentsProgrammingScore.id].ProgrammingTotalScore = studentsProgrammingScore.totalScore;
            finalScores[studentsProgrammingScore.id].ProgrammingEveryScore = pStringEveryScore;
            finalScores[studentsProgrammingScore.id].ProgrammingScoreString = stringP;

        }

        for (const courseScore of this.courseScores) {

            let modellingString = '';
            let quizString = '';
            let programmingString = '';
            let quizEveryScore = {};
            let modelingEveryScore = {};
            let programmingEveryScore = {};

            for (const i in this.allModellingExercises) {
                modellingString += '0,';
                modelingEveryScore = {
                    'exID': this.allModellingExercises[i].exID, 'exTitle': this.allModellingExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for (const i in this.allQuizExercises) {
                quizString += '0,';
                quizEveryScore = {
                    'exID': this.allQuizExercises[i].exID, 'exTitle': this.allQuizExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for (const i in this.allProgrammingExercises) {
                programmingString += '0,';
                programmingEveryScore = {
                    'exID': this.allProgrammingExercises[i].exID, 'exTitle': this.allProgrammingExercises[i].title,
                    'absoluteScore': 0
                };
            }

            const student = courseScore.participation.student;
            if (!finalScores[student.id]) {
                finalScores[student.id] = {
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
                    'ModellingTotalScore': 0,
                    'ModellingEveryScore': modelingEveryScore,
                    'ModellingScoreString': modellingString,
                    'OverallScore': 0
                };
            } else {
                finalScores[student.id].OverallScore = finalScores[student.id].QuizTotalScore
                    + finalScores[student.id].ProgrammingTotalScore + finalScores[student.id].ModellingTotalScore;
            }
        }

        this.finalScores = Object.keys(finalScores).map(key => finalScores[key]);
        this.exportReady = true;
    }

    exportResults() {

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
                const modelling = result.ModellingTotalScore;
                const score = result.OverallScore;
                const quizString = result.QuizScoreString;
                const modellingString = result.ModellingScoreString;
                const programmingString = result.ProgrammingScoreString;


                if (index === 0) {
                    rows.push('data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,QuizTotalScore,' + this.titleQuizString + 'ProgrammingTotalScore,' + this.titleProgrammingString + 'ModellingTotalScore,' + this.titleModellingString + 'OverallScore');
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quiz + ',' + quizString + '' + programming + ',' + programmingString + '' + modelling + ',' + modellingString + '' + score);
                } else {
                    rows.push(firstName + ',' + lastName + ',' + studentId + ',' + email + ',' + quiz + ',' + quizString + '' + programming + ',' + programmingString + '' + modelling + ',' + modellingString + '' + score);
                }
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'course_' + this.course.title + '-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }

    callback() {
    }
}


Number.prototype.round = function(places) {
    return +(Math.round(this + "e+" + places)  + "e-" + places);
}
