import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseService, ExerciseType } from '../entities/exercise';
import { Course, CourseService } from '../entities/course';
import { ResultService } from '../entities/result/result.service';
import { DifferencePipe } from 'angular2-moment';
import { ParticipationService } from '../entities/participation/participation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { ResultDetailComponent } from '../entities/result/result-detail.component';
import { ModelingAssessmentService } from '../entities/modeling-assessment/modeling-assessment.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-instructor-dashboard',
    templateUrl: './instructor-dashboard.component.html',
    providers:  [
        JhiAlertService, ModelingAssessmentService
    ]
})

export class InstructorDashboardComponent implements OnInit, OnDestroy {

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    showAllResults: string;
    results: Result[];
    allResults: Result[];
    eventSubscriber: Subscription;

    constructor(private route: ActivatedRoute,
                private momentDiff: DifferencePipe,
                private courseService: CourseService,
                private exerciseService: ExerciseService,
                private resultService: ResultService,
                private modelingAssessmentService: ModelingAssessmentService,
                private participationService: ParticipationService,
                private modalService: NgbModal,
                private eventManager: JhiEventManager) {
        this.reverse = false;
        this.predicate = 'id';
        this.showAllResults = 'all';
        this.results = [];
        this.allResults = [];
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                this.exercise = res.body;
                this.getResults();
            });
        });
        this.registerChangeInCourses();
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getResults());
    }

    getResults() {
        this.resultService.getResultsForExercise(this.exercise.course.id, this.exercise.id, {
            showAllResults: this.showAllResults,
            ratedOnly: this.exercise.type === ExerciseType.QUIZ,
            withSubmissions: this.exercise.type === ExerciseType.MODELING,
            withAssessors: this.exercise.type === ExerciseType.MODELING
        }).subscribe((res: HttpResponse<Result[]>) => {
            const tempResults: Result[] = res.body;
            tempResults.forEach(function(result: Result) {
                result.participation.results = [result];
            });
            this.allResults = tempResults;
            this.filterResults();
        });
    }

    filterResults() {
        this.results = [];
        if (this.showAllResults === 'successful') {
            this.results = this.allResults.filter(function(result) {
                return result.successful === true;
            });
        } else if (this.showAllResults === 'unsuccessful') {
            this.results = this.allResults.filter(function(result) {
                return result.successful === false;
            });
        } else if (this.showAllResults === 'all') {
            this.results = this.allResults;
        }
    }

    durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    goToBuildPlan(result: Result) {
        this.participationService.buildPlanWebUrl(result.participation.id).subscribe(res => {
            window.open(res.url);
        });
    }

    goToRepository(result: Result) {
        window.open(result.participation.repositoryUrl);
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(ResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
    }

    toggleShowAllResults(newValue: string) {
        this.showAllResults = newValue;
        this.filterResults();
    }

    exportNames() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                let studentName = result.participation.student.firstName;
                if (result.participation.student.lastName != null && result.participation.student.lastName !== '') {
                    studentName = studentName + ' ' + result.participation.student.lastName;
                }
                rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-names.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    exportResults() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                let studentName = result.participation.student.firstName;
                if (result.participation.student.lastName != null && result.participation.student.lastName !== '') {
                    studentName = studentName + ' ' + result.participation.student.lastName;
                }
                const studentId = result.participation.student.login;
                const score = result.score;

                if (index === 0) {
                    if (this.exercise.type === ExerciseType.QUIZ) {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score');
                    } else {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score, Repo Link');
                    }
                }
                if (this.exercise.type === ExerciseType.QUIZ) {
                    rows.push(studentName + ', ' + studentId + ', ' + score);
                } else {
                    const repoLink = result.participation.repositoryUrl;
                    rows.push(studentName + ', ' + studentId + ', ' + score + ', ' + repoLink);
                }
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    refresh() {
        this.getResults();
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() { }
}
