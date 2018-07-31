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
        });

        this.courseScoresService.find(courseId).subscribe(res => {
            this.courseScores = res;
            this.groupResults();
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


        console.log('Maximal zu erreichende Punkte von Q: ' + maximalZuErreichendePunkteQ);
        console.log('Maximal zu erreichende Punkte von M: ' + maximalZuErreichendePunkteM);
        console.log('Maximal zu erreichende Punkte von P: ' + maximalZuErreichendePunkteP);
        console.log('Titel Quizzes:' + this.titleQuizString);
        console.log('Titel Modelling:' + this.titleModellingString);
        console.log('Titel Programming:' + this.titleProgrammingString);

        // Successful Participations as the total amount and a relative value to all Exercises
        for (const result of this.results) {

            if (result.successful) {
                rows[result.participation.student.id].successful++;
                //TODO delete the following line
                //rows[result.participation.student.id].successfullyCompletedInPercent = (rows[result.participation.student.id].successful / this.numberOfExercises) * 100;
            }
        }

        for (const studentId in rows) {
            //TODO test
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

        for (const score of this.courseScores) {
            if (score.participation.student) {
                rows[score.participation.student.id].overallScore = score.score; //TODO: this line of code gives you not the overall score!!!! FDE
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

                        //TODO: delete, we handle this case below
                        // studentsQuizScores[student.id].totalScore += Math.round((result.score * exercise.maxScore) / 100);

                        studentsQuizScores[student.id].scoreListQ[exercise.id] = {
                            'completionDate': resultCompletionDate,
                            'exID': exercise.id,
                            'exTitle': exercise.title,
                            'absoluteScore': Math.round((result.score * exercise.maxScore) / 100)
                        };
                    }
                    break;

                case "programming-exercise":
                    if (resultCompletionDate.getTime() <= dueDate.getTime()) {

                        const score = studentsProgrammingScores[student.id].scoreListP[exercise.id];
                        if (score == null) {// || completionDate.getTime() > score.completionDate.getTime()) {    // we want to have the last result withing the due date (see above)
                            studentsProgrammingScores[student.id].scoreListP[exercise.id] = {
                                'completionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': Math.round((result.score * exercise.maxScore) / 100)
                            };
                        }
                        else if (resultCompletionDate.getTime() > score.completionDate.getTime()) {
                            studentsProgrammingScores[student.id].scoreListP[exercise.id] = {
                                'completionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': Math.round((result.score * exercise.maxScore) / 100)
                            };
                        }
                    }
                    break;

                case "modeling-exercise":
                    // we can also have results (due to the manual assessment) that appear after the completion date
                    // if (completionDate.getTime() <= dueDate.getTime()) {

                        const score = studentsModelingScores[student.id].scoreListM[exercise.id];
                        if(score == null || resultCompletionDate.getTime() > score.completionDate) {     // we want to have the last result
                            studentsModelingScores[student.id].scoreListM[exercise.id] = {
                                'completionDate': resultCompletionDate,
                                'exID': exercise.id,
                                'exTitle': exercise.title,
                                'absoluteScore': Math.round((result.score * exercise.maxScore) / 100)
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


        for (const q of this.typeQ) {
            let stringQ = '';
            let qStringEveryScore = {};


            for (const quizzes in this.allQuizExercises) {
                let bool = true;
                let exId = this.allQuizExercises[quizzes].exId;

                for (const scoresQ in q.scoreListQ) {
                    let exID = q.scoreListQ[scoresQ].exID;
                    if (exId == exID) {
                        bool = false;
                        stringQ += q.scoreListQ[scoresQ].absoluteScore + ',';
                        qStringEveryScore = {
                            'exID': exID, 'exTitle': this.allQuizExercises[quizzes].title,
                            'absoluteScore': q.scoreListQ[scoresQ].absoluteScore
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

            if (!finalScores[q.id]) {
                finalScores[q.id] = {
                    'firstName': q.firstName,
                    'lastName': q.lastName,
                    'login': q.login,
                    'email': q.email,
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
            finalScores[q.id].QuizTotalScore = q.totalScore;
            finalScores[q.id].QuizEveryScore = qStringEveryScore;
            finalScores[q.id].QuizScoreString = stringQ;
        }

        for (const m of this.typeM) {

            let stringM = '';
            let mStringEveryScore = {};

            for (const modellings in this.allModellingExercises) {
                let bool = true;
                let exId = this.allModellingExercises[modellings].exId;

                for (const scores in m.scoreListM) {
                    let exID = m.scoreListM[scores].exID;
                    if (exId == exID) {
                        bool = false;
                        stringM += m.scoreListM[scores].absoluteScore + ',';
                        mStringEveryScore = {
                            'exID': exID, 'exTitle': this.allModellingExercises[modellings].title,
                            'absoluteScore': m.scoreListM[scores].absoluteScore
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


            finalScores[m.id].ModellingTotalScore = m.totalScore;
            finalScores[m.id].ModellEveryScore = mStringEveryScore;
            finalScores[m.id].ModellingScoreString = stringM;


        }


        for (const p of this.typeP) {

            let stringP = '';
            let pStringEveryScore = {};

            for (const programmings in this.allProgrammingExercises) {
                let bool = true;
                let exId = this.allProgrammingExercises[programmings].exId;

                for (const scores in p.scoreListP) {
                    let exID = p.scoreListP[scores].exID;
                    if (exId == exID) {
                        bool = false;
                        stringP += p.scoreListP[scores].absoluteScore + ',';
                        pStringEveryScore = {
                            'exID': exID, 'exTitle': this.allProgrammingExercises[programmings].title,
                            'absoluteScore': p.scoreListP[scores].absoluteScore
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

            finalScores[p.id].ProgrammingTotalScore = p.totalScore;
            finalScores[p.id].ProgrammingEveryScore = pStringEveryScore;
            finalScores[p.id].ProgrammingScoreString = stringP;

        }

        for (const c of this.courseScores) {

            let modellingString = '';
            let quizString = '';
            let programmingString = '';
            let qEveryScore = {};
            let mEveryScore = {};
            let pEveryScore = {};

            for (var i in this.allModellingExercises) {
                modellingString += '0,';
                mEveryScore = {
                    'exID': this.allModellingExercises[i].exID, 'exTitle': this.allModellingExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for (var i in this.allQuizExercises) {
                quizString += '0,';
                qEveryScore = {
                    'exID': this.allQuizExercises[i].exID, 'exTitle': this.allQuizExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for (var i in this.allProgrammingExercises) {
                programmingString += '0,';
                pEveryScore = {
                    'exID': this.allProgrammingExercises[i].exID, 'exTitle': this.allProgrammingExercises[i].title,
                    'absoluteScore': 0
                };
            }

            if (!finalScores[c.participation.student.id]) {
                finalScores[c.participation.student.id] = {
                    'firstName': c.participation.student.firstName,
                    'lastName': c.participation.student.lastName,
                    'login': c.participation.student.login,
                    'email': c.participation.student.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': qEveryScore,
                    'QuizScoreString': quizString,
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore': pEveryScore,
                    'ProgrammingScoreString': programmingString,
                    'ModellingTotalScore': 0,
                    'ModellingEveryScore': mEveryScore,
                    'ModellingScoreString': modellingString,
                    'OverallScore': 0
                };
            } else {
                finalScores[c.participation.student.id].OverallScore = finalScores[c.participation.student.id].QuizTotalScore
                    + finalScores[c.participation.student.id].ProgrammingTotalScore + finalScores[c.participation.student.id].ModellingTotalScore;
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
