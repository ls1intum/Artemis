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
    finalScores = [];
    exportReady = false;
    allScores: {};
    exercisesQ = '';
    exercisesP = '';
    exercisesM = '';



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

        this.courseService.find(courseId).subscribe(res => {
            this.allScores = res;
            this.groupResults();

        });    }

    groupResults() {
        if (!this.results || !this.participations || !this.courseScores || this.participations.length === 0 || this.results.length === 0 || this.courseScores.length === 0) {
            return;
        }

        const rows = {};
        const exercisesSeen = {};
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
            }
        }

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

        const typeQ = {}; //hashmap for Question type exercises
        const typeP = {}; //hashmap for Programming type exercises
        const typeM = {}; //hashmap for Modelling type exercises
        const individualExercisesSeen = {}; //Stores information to reduce redundancies

        const scoreExerciseP = {};
        const scoreExerciseQ = {};
        const scoreExerciseM = {};

        for (const result of this.results) {

            if(!typeP[result.participation.student.id]){
                typeP[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'email': result.participation.student.email,
                    'exType': 'programming-exercise',
                    'scoreList': scoreExerciseP,
                    'totalScore':0
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
                    'scoreList': scoreExerciseQ,
                    'totalScore': 0

                };
            }
            if (!typeM[result.participation.student.id]) {
                typeM[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'email': result.participation.student.email,
                    'exType': 'modelling-exercise',
                    'scoreList': scoreExerciseM,
                    'totalScore': 0
                };
            }

            if (!individualExercisesSeen[result.id]) {

                individualExercisesSeen[result.id] = true;



                if (result.successful) {
                    switch (result.participation.exercise.type) {
                        case "quiz":

                            scoreExerciseM[result.participation.exercise.id] = {
                                'exID':result.participation.exercise.id,
                                'exTitle':result.participation.exercise.title,
                                'score': result.score
                            };
                            typeQ[result.participation.student.id].scoreList = scoreExerciseQ;

                            typeQ[result.participation.student.id].totalScore += result.score;



                            break;

                        case "programming-exercise":
                            scoreExerciseP[result.participation.exercise.id] = {
                                'exID':result.participation.exercise.id,
                                'exTitle':result.participation.exercise.title,
                                'score': result.score
                            };
                            typeP[result.participation.student.id].scoreList = scoreExerciseP;

                            typeP[result.participation.student.id].totalScore += result.score;


                            break;
                        case "modelling-exercise":
                            scoreExerciseM[result.participation.exercise.id] = {
                                'exID':result.participation.exercise.id,
                                'exTitle':result.participation.exercise.title,
                                'score': result.score
                            };
                            typeM[result.participation.student.id].scoreList = scoreExerciseM;

                            typeM[result.participation.student.id].totalScore += result.score;


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

        const scoreExerciseP = {};
        const scoreExerciseQ = {};
        const scoreExerciseM = {};



        for (const q of this.typeQ) {
            if(!finalScores[q.id]){
                finalScores[q.id] = {
                    'firstName': q.firstName,
                    'lastName': q.lastName,
                    'login': q.login,
                    'email': q.email,
                    'QuizTotalScore': 0,
                    'quizScoreList': scoreExerciseQ,
                    'ProgrammingTotalScore': 0,
                    'programmingScoreList': scoreExerciseP,
                    'ModellingTotalScore': 0,
                    'modellingScoreList': scoreExerciseM,
                    'OverallScore': 0,
                    'stringQ':'',
                    'stringP':'',
                    'stringM':'',


                };
            }

            finalScores[q.id].QuizTotalScore = q.totalScore;
            finalScores[q.id].quizScoreList = q.scoreList;

        }

        for (const m of this.typeM) {
            finalScores[m.id].ModellingTotalScore = m.totalScore;
            finalScores[m.id].modellingScoreList = m.scoreList;
        }

        for (const p of this.typeP) {
            finalScores[p.id].ProgrammingTotalScore = p.totalScore;
            finalScores[p.id].programmingScoreList = p.scoreList;

        }

        for (const c of this.courseScores) {
            if(!finalScores[c.participation.student.id]){
                finalScores[c.participation.student.id] = {
                    'firstName': c.participation.student.firstName,
                    'lastName': c.participation.student.lastName,
                    'login': c.participation.student.login,
                    'email': c.participation.student.email,
                    'QuizTotalScore': 0,
                    'quizStoreList': scoreExerciseQ,
                    'ProgrammingTotalScore': 0,
                    'programmingStoreList':scoreExerciseP,
                    'ModellingTotalScore': 0,
                    'modellingStoreList':scoreExerciseM,
                    'OverallScore': c.score,
                    'stringQ':'',
                    'stringP':'',
                    'stringM':''
                };
            } else {
                finalScores[c.participation.student.id].OverallScore = c.score;
            }

        }

        this.finalScores = Object.keys(finalScores).map(key => finalScores[key]);
        this.exportReady = true;
    }

    exportResults() {


        this.getAllScoresForAllCourseParticipants();




        //get names of total exercises
        const exercisesSeen = {};
        const totalQuizes={};
        const totalProgramming = {};
        const totalModelling = {};

        var exerciseNumber = 0;

        for (const p of this.participations) {

            if (!exercisesSeen[p.exercise.id]) {
                exercisesSeen[p.exercise.id] = true;
                exerciseNumber ++;
                if(p.exercise.type =='programming-exercise'){
                    totalProgramming[p.exercise.id] = {
                        'exId': p.exercise.id,
                        'exTitle': p.exercise.title
                    }
                    this.exercisesP += p.exercise.title+',';
                }

                else if(p.exercise.type == 'modelling-exercise '){
                    totalModelling[p.exercise.id] = {
                        'exId': p.exercise.id,
                        'exTitle': p.exercise.title
                    }

                    this.exercisesM += p.exercise.title +',';

                }
                else{

                    this.exercisesQ += p.exercise.title+',';
                    totalQuizes[p.exercise.id] = {
                        'exId': p.exercise.id,
                        'exTitle': p.exercise.title
                    }
                }

            }
        }

        if (this.exportReady && this.finalScores.length > 0) {

            console.log(this.finalScores);
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



                for (const j in totalProgramming){//go through all the programming exercises

                    var exTitle = totalProgramming[j].exTitle;

                    if(result.programmingScoreList){
                        for(const i in result.programmingScoreList){ //go through all the students list of programming exercises

                            if(exTitle == result.programmingScoreList[i].exTitle){
                                result.stringP += result.programmingScoreList[i].score+',';
                            }

                        }
                    }
                    else
                        result.stringP += 0 + ',';


                }

                for (const j in totalQuizes){//go through all the programming exercises

                    var exTitle = totalQuizes[j].exTitle;
                    for(const i in result.totalQuizes){ //go through all the students list of programming exercises


                        if(exTitle == result.totalQuizes[i].exTitle){
                            result.stringQ += result.totalQuizes[i].score +',';
                        }
                        else
                            result.stringQ += 0 + ',';

                    }
                }

                for (const j in totalModelling){//go through all the programming exercises

                    var exTitle = totalModelling[j].exTitle;
                    for(const i in result.totalModelling){ //go through all the students list of programming exercises


                        if(exTitle == result.totalModelling[i].exTitle){
                            result.stringM += result.totalModelling[i].score+',';;
                        }
                        else
                            result.stringM += 0 + ',';

                    }
                }


                const scoresQ = result.stringQ;
                const scoresP = result.stringP;
                const scoresM = result.stringM;


                /*
                                console.log(quiz);
                                console.log(scoresQ);

                                console.log(programming);
                                console.log(scoresP);

                                console.log(modelling);
                                console.log(scoresM);

                                console.log('totali '+ score);



                */
                if (index === 0) {
                    rows.push('data:text/csv;charset=utf-8,FirstName,LastName,TumId,Email,' +this.exercisesQ+ 'QuizTotalScore,' +this.exercisesP+  'ProgrammingTotalScore,' +this.exercisesM+  'ModellingTotalScore,OverallScore');
                    rows.push(firstName + ',' + lastName + ',' + studentId + ','+ email+ ',' + scoresQ  +   quiz + ',' + scoresP + programming + ',' + scoresM +  modelling + ', ' + score);
                } else {
                    rows.push(firstName + ',' + lastName + ',' + studentId + ','+ email+ ',' + scoresQ  +   quiz + ',' + scoresP + programming + ',' + scoresM +  modelling + ',' + score);
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

    ngOnDestroy(){
        this.paramSub.unsubscribe();
    }

    callback() { }
}
