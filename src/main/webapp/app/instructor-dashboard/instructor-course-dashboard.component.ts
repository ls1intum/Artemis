import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseParticipationService, CourseResultService, CourseScoresService, CourseService } from '../entities/course';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers:  [
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
    participations = [];
    courseScores = [];
    typeQ = [];
    typeP = [];
    typeM = [];
    allQuizExercises = [];
    allProgrammingExercises = [];
    allModellingExercises = [];
    titleQuizString ='';
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
        let maximalZuErreichendePunkteQ= 0;
        let maximalZuErreichendePunkteM= 0;
        let maximalZuErreichendePunkteP= 0;

        for (const p of this.participations) {
            if (!rows[p.student.id]) {
                rows[p.student.id] = {
                    'firstName': p.student.firstName,
                    'lastName': p.student.lastName,
                    'login': p.student.login,
                    'participated': 0,
                    'participationInPercent': 0,
                    'successful': 0,
                    'successfullyCompletedInPercent': 0,
                    'overallScore': 0,
                };
            }

            rows[p.student.id].participated++;

            if (!exercisesSeen[p.exercise.id]) {
                exercisesSeen[p.exercise.id] = true;
                this.numberOfExercises++;

                switch (p.exercise.type) {
                    case "quiz":
                        maximalZuErreichendePunkteQ += p.exercise.maxScore;

                        this.allQuizExercises[p.exercise.id] = {
                            'exId': p.exercise.id,
                            'exTitle': p.exercise.title
                        };
                        this.titleQuizString += p.exercise.title+',';

                        break;

                    case "programming-exercise":
                        maximalZuErreichendePunkteP += p.exercise.maxScore;

                        this.allProgrammingExercises[p.exercise.id] = {
                            'exId': p.exercise.id,
                            'exTitle': p.exercise.title
                        };
                        this.titleProgrammingString += p.exercise.title+',';

                        break;
                    case "modeling-exercise":
                        maximalZuErreichendePunkteM += p.exercise.maxScore;

                        this.allModellingExercises[p.exercise.id] = {
                            'exId': p.exercise.id,
                            'exTitle': p.exercise.title
                        };
                        this.titleModellingString += p.exercise.title+',';
                        break;

                    default:
                }

            }
        }


        console.log('Maximal zu erreichende Punkte von Q: '+maximalZuErreichendePunkteQ);
        console.log('Maximal zu erreichende Punkte von M: '+maximalZuErreichendePunkteM);
        console.log('Maximal zu erreichende Punkte von P: '+maximalZuErreichendePunkteP);
        console.log('Titel Quizzes:'+this.titleQuizString);
        console.log('Titel Modelling:'+this.titleModellingString);
        console.log('Titel Programming:'+this.titleProgrammingString);

        // Successful Participations as the total amount and a relative value to all Exercises
        for (const r of this.results) {
            rows[r.participation.student.id].successful++;
            rows[r.participation.student.id].successfullyCompletedInPercent = (rows[r.participation.student.id].successful / this.numberOfExercises) * 100;
        }

        // Relative amount of participation in all exercises
        // Since 1 user can have multiple participations studentSeen makes sure each student is only calculated once
        const studentSeen = {};
        for (const p of this.participations) {
            if (!studentSeen[p.student.id]) {
                studentSeen[p.student.id] = true;
                rows[p.student.id].participationInPercent = (rows[p.student.id].participated / this.numberOfExercises) * 100;
            }
        }

        // Set the total score of all Exercises (cache-control: no-cache, no-store, max-age=0, must-revalidate
        // connection: close
        // content-type: application/json; charset=UTF-8
        // date: Sat, 14 Jul 2018 08:34:10 GMT
        // expires: 0
        // pragma: no-cache
        // set-cookie: XSRF-TOKEN=b9a821c6-f7bf-4b31-9a96-e833a930f40f; path=/
        // transfer-encoding: chunked
        // x-application-context: ArTEMiS:dev,bamboo,bitbucket,jira:8080
        // x-content-type-options: nosniff
        // x-powered-by: Express
        // x-xss-protection: 1; mode=blockas mentioned on the RESTapi division by amount of exercises)
        for (const s of this.courseScores) {
            if (s.participation.student) {
                rows[s.participation.student.id].overallScore = s.score;
            }
        }
        this.rows = Object.keys(rows).map(key => rows[key]);
    }

    getAllScoresForAllCourseParticipants(){

        if (!this.results || !this.participations || !this.courseScores || this.participations.length === 0 || this.results.length === 0 || this.courseScores.length === 0) {
            return;
        }


        const typeQ = {};
        const typeP = {};
        const typeM = {};
        const individualExercisesSeen = {};

        for (const result of this.results) {


            if(!typeP[result.participation.student.id]){
                typeP[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'email': result.participation.student.email,
                    'exType': 'programming-exercise',
                    'totalScore': 0,
                    'scoreListP': {},
                    'scoreListPString': ''
                };
            }
            if(!typeQ[result.participation.student.id]){
                typeQ[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'email': result.participation.student.email,
                    'exType': 'quiz',
                    'totalScore': 0,
                    'scoreListQ': {},
                    'scoreListQString': ''
                };
            }
            if (!typeM[result.participation.student.id]) {
                typeM[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'email': result.participation.student.email,
                    'exType': 'modeling-exercise',
                    'totalScore': 0,
                    'scoreListM': {},
                    'scoreListMString': ''
                };
            }

            if (!individualExercisesSeen[result.id]) {

                individualExercisesSeen[result.id] = true;

                if (result.successful) {
                    switch (result.participation.exercise.type) {
                        case "quiz":

                            typeQ[result.participation.student.id].totalScore += (result.score * result.participation.exercise.maxScore)/100;


                            typeQ[result.participation.student.id].scoreListQ[result.participation.exercise.id] = {'exID':result.participation.exercise.id, 'exTitle':result.participation.exercise.title,
                                'absoluteScore': (result.score * result.participation.exercise.maxScore)/100
                            };
                            /*
                            typeQ[result.participation.student.id].exerciseTitleQString += ','+result.participation.exercise.title;
                            typeQ[result.participation.student.id].scoreListQString += ','+(result.score * result.participation.exercise.maxScore)/100;
                            */
/*
                            console.log('Student: '+result.participation.student.firstName);
                            console.log('QuizStringTitel'+ typeQ[result.participation.student.id].exerciseTitleQString);
                            console.log('ScoreListQString'+ typeQ[result.participation.student.id].scoreListQString);*/
                            /*console.log('Q result score: '+ result.score);
                           // console.log('Q result max_score: '+ result.score.maxScore); alscher output ist immer null
                            console.log('Q exercise max_score: '+result.participation.exercise.maxScore);
                           // console.log('Q exercise score: '+result.participation.exercise.score); falscher output ist immer null
                            */

                            break;

                        case "programming-exercise":
                            typeP[result.participation.student.id].totalScore += (result.score * result.participation.exercise.maxScore)/100;

                            typeP[result.participation.student.id].scoreListP[result.participation.exercise.id] = {'exID':result.participation.exercise.id, 'exTitle':result.participation.exercise.title,
                                'absoluteScore': (result.score * result.participation.exercise.maxScore)/100
                            };
                            /*

                            typeP[result.participation.student.id].exerciseTitlePString += ','+result.participation.exercise.title;
                            typeP[result.participation.student.id].scoreListPString += ','+(result.score * result.participation.exercise.maxScore)/100;
                            */
/*
                            console.log('P result score: '+ result.score);
                          // console.log('Q result max_score: '+ result.score.maxScore); alscher output ist immer null
                           console.log('P exercise max_score: '+result.participation.exercise.maxScore);
                          // console.log('Q exercise score: '+result.participation.exercise.score); falscher output ist immer null
*/
                            break;

                        case "modeling-exercise":
                            typeM[result.participation.student.id].totalScore += (result.score * result.participation.exercise.maxScore)/100;

                            typeM[result.participation.student.id].scoreListM[result.participation.exercise.id] = {'exID':result.participation.exercise.id, 'exTitle':result.participation.exercise.title,
                                'absoluteScore': (result.score * result.participation.exercise.maxScore)/100
                            };

                            /*

                            typeM[result.participation.student.id].exerciseTitleMString += ','+result.participation.exercise.title;
                            typeM[result.participation.student.id].scoreListMString += ','+(result.score * result.participation.exercise.maxScore)/100;
                            */
/*
                            console.log('M result score: '+ result.score);
                          // console.log('Q result max_score: '+ result.score.maxScore); alscher output ist immer null
                           console.log('M exercise max_score: '+result.participation.exercise.maxScore);
                          // console.log('Q exercise score: '+result.participation.exercise.score); falscher output ist immer null*/

                            break;
                        default:
                    }
                }
            }
        }



        this.typeQ = Object.keys(typeQ).map(key => typeQ[key]);
        this.typeP = Object.keys(typeQ).map(key => typeP[key]);
        this.typeM = Object.keys(typeQ).map(key => typeM[key]);

        this.mergeScoresForExerciseCategories();
    }

    mergeScoresForExerciseCategories(){

        const finalScores = {};


        for (const q of this.typeQ) {
            let stringQ ='';
            let qStringEveryScore = {};


            for(const quizzes in this.allQuizExercises){
                let bool = true;
                let exId= this.allQuizExercises[quizzes].exId;

                for(const scoresQ in q.scoreListQ){
                    let exID= q.scoreListQ[scoresQ].exID;
                    if(exId==exID){
                        bool= false;
                        stringQ += q.scoreListQ[scoresQ].absoluteScore+',';
                        qStringEveryScore = {'exID': exID, 'exTitle': this.allQuizExercises[quizzes].title,
                            'absoluteScore': q.scoreListQ[scoresQ].absoluteScore
                        };
                    }
                }
                if(bool){
                    qStringEveryScore = {'exID': exId, 'exTitle': this.allQuizExercises[quizzes].title,
                        'absoluteScore': 0
                    };
                    stringQ += '0,';
                }
            }

            console.log('StringQ:  '+stringQ);

            if(!finalScores[q.id]){
                finalScores[q.id] = {
                    'firstName': q.firstName,
                    'lastName': q.lastName,
                    'login': q.login,
                    'email': q.email,
                    'QuizTotalScore': 0,
                    'QuizEveryScore': {},
                    'QuizScoreString': '',
                    'ProgrammingTotalScore': 0,
                    'ProgrammingEveryScore':{},
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

            for(const modellings in this.allModellingExercises){
                let bool = true;
                let exId= this.allModellingExercises[modellings].exId;

                for(const scores in m.scoreListM){
                    let exID= m.scoreListM[scores].exID;
                    if(exId==exID){
                        bool= false;
                        stringM += m.scoreListM[scores].absoluteScore+',';
                        mStringEveryScore = {'exID': exID, 'exTitle': this.allModellingExercises[modellings].title,
                            'absoluteScore': m.scoreListM[scores].absoluteScore
                        };
                    }
                }
                if(bool){
                    mStringEveryScore = {'exID': exId, 'exTitle': this.allModellingExercises[modellings].title,
                        'absoluteScore': 0
                    };
                    stringM += '0,';
                }
            }

            console.log('StringM:  '+stringM);




            finalScores[m.id].ModellingTotalScore = m.totalScore;
            finalScores[m.id].ModellEveryScore = mStringEveryScore;
            finalScores[m.id].ModellingScoreString= stringM;


        }

        for (const p of this.typeP) {

            let stringP = '';
            let pStringEveryScore = {};

            for(const programmings in this.allProgrammingExercises){
                let bool = true;
                let exId= this.allProgrammingExercises[programmings].exId;

                for(const scores in p.scoreListP){
                    let exID= p.scoreListP[scores].exID;
                    if(exId==exID){
                        bool= false;
                        stringP += p.scoreListP[scores].absoluteScore+',';
                        pStringEveryScore = {'exID': exID, 'exTitle': this.allProgrammingExercises[programmings].title,
                            'absoluteScore': p.scoreListP[scores].absoluteScore
                        };
                    }
                }
                if(bool){
                    pStringEveryScore = {'exID': exId, 'exTitle': this.allProgrammingExercises[programmings].title,
                        'absoluteScore': 0
                    };
                    stringP += '0,';
                }
            }


            console.log('StringP: '+stringP);

            finalScores[p.id].ProgrammingTotalScore = p.totalScore;
            finalScores[p.id].ProgrammingEveryScore = pStringEveryScore;
            finalScores[p.id].ProgrammingScoreString = stringP;

        }

        for (const c of this.courseScores) {

            let modellingString = '';
            let quizString ='';
            let programmingString ='';
            let qEveryScore = {};
            let mEveryScore = {};
            let pEveryScore = {};

            for(var i in this.allModellingExercises){
                modellingString += '0,';
                mEveryScore = {'exID': this.allModellingExercises[i].exID, 'exTitle': this.allModellingExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for(var i in this.allQuizExercises){
                quizString += '0,';
                qEveryScore = {'exID': this.allQuizExercises[i].exID, 'exTitle': this.allQuizExercises[i].title,
                    'absoluteScore': 0
                };
            }
            for(var i in this.allProgrammingExercises){
                programmingString += '0,';
                pEveryScore = {'exID': this.allProgrammingExercises[i].exID, 'exTitle': this.allProgrammingExercises[i].title,
                    'absoluteScore': 0
                };
            }

            if(!finalScores[c.participation.student.id]){
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

                const firstName = result.firstName;
                const lastName = result.lastName;
                const studentId = result.login;
                const email = result.email;
                const quiz = result.QuizTotalScore;
                const programming = result.ProgrammingTotalScore;
                const modelling = result.ModellingTotalScore;
                const score = result.OverallScore;
                const quizString = result.QuizScoreString;
                const modellingString = result.ModellingScoreString;
                const programmingString = result.ProgrammingScoreString;


                if (index === 0) {
                    rows.push('data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,QuizTotalScore,'+this.titleQuizString+'ProgrammingTotalScore,'+this.titleProgrammingString+'ModellingTotalScore,'+this.titleModellingString+'OverallScore');
                    rows.push(firstName + ', ' + lastName + ', ' + studentId + ', ' +email+', '+ quiz + ', ' + quizString+ ' ' + programming + ', ' + programmingString+ ' ' +modelling + ', ' +modellingString+ ' ' + score );
                } else {
                    rows.push(firstName + ', ' + lastName + ', ' + studentId + ', '+email+', ' + quiz + ', ' + quizString+ ' ' + programming + ', ' + programmingString+ ' ' +modelling + ', ' +modellingString+ '' + score );
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

    callback() { }
}
