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

        // Set the total score of all Exercises (as mentioned on the RESTapi division by amount of exercises)
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
                    'exType': 'programming-exercise',
                    'totalScore': 0,
                    'amountOfExercise': 0
                };
            }
            if(!typeQ[result.participation.student.id]){
                typeQ[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'exType': 'quiz',
                    'totalScore': 0,
                    'amountOfExercise': 0
                };
            }
            if (!typeM[result.participation.student.id]) {
                typeM[result.participation.student.id] = {
                    'firstName': result.participation.student.firstName,
                    'lastName': result.participation.student.lastName,
                    'id': result.participation.student.id,
                    'login': result.participation.student.login,
                    'exType': 'modelling-exercise',
                    'totalScore': 0,
                    'amountOfExercise': 0
                };
            }

            if (!individualExercisesSeen[result.id]) {

                individualExercisesSeen[result.id] = true;

                if (result.successful) {
                    switch (result.participation.exercise.type) {
                        case "quiz":
                            typeQ[result.participation.student.id].totalScore += result.score;
                            typeQ[result.participation.student.id].amountOfExercise += 1;
                            break;

                        case "programming-exercise":
                            typeP[result.participation.student.id].totalScore += result.score;
                            typeQ[result.participation.student.id].amountOfExercise += 1;
                            break;

                        case "modelling-exercise":
                            typeM[result.participation.student.id].totalScore += result.score;
                            typeQ[result.participation.student.id].amountOfExercise += 1;
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




            if(!finalScores[q.id]){
                finalScores[q.id] = {
                    'firstName': q.firstName,
                    'lastName': q.lastName,
                    'login': q.login,
                    'QuizTotalScore': 0,
                    'ProgrammingTotalScore': 0,
                    'ModellingTotalScore': 0,
                    'OverallScore': 0
                };
            }
            finalScores[q.id].QuizTotalScore = q.totalScore;
        }

        for (const m of this.typeM) {
            finalScores[m.id].ModellingTotalScore = m.totalScore;
        }

        for (const p of this.typeP) {
            finalScores[p.id].ModellingTotalScore = p.totalScore;
        }

        for (const c of this.courseScores) {
            if(!finalScores[c.participation.student.id]){
                finalScores[c.participation.student.id] = {
                    'firstName': c.participation.student.firstName,
                    'lastName': c.participation.student.lastName,
                    'login': c.participation.student.login,
                    'QuizTotalScore': 0,
                    'ProgrammingTotalScore': 0,
                    'ModellingTotalScore': 0,
                    'OverallScore': c.score
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

        if (this.exportReady && this.finalScores.length > 0) {
            const rows = [];
            this.finalScores.forEach((result, index) => {

                const firstName = result.firstName;
                const lastName = result.lastName;
                const studentId = result.login;
                const quiz = result.QuizTotalScore;
                const programming = result.ProgrammingTotalScore;
                const modelling = result.ModellingTotalScore;
                const score = result.OverallScore;

                if (index === 0) {
                    rows.push('data:text/csv;charset=utf-8,FirstName,LastName,TumId,QuizTotalScore,ProgrammingTotalScore,ModellingTotalScore,OverallScore');
                    rows.push(firstName + ', ' + lastName + ', ' + studentId + ', ' + quiz + ', ' + programming + ', ' + modelling + ', ' + score );
                } else {
                    rows.push(firstName + ', ' + lastName + ', ' + studentId + ', ' + quiz + ', ' + programming + ', ' + modelling + ', ' + score );
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
