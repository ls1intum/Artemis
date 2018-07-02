import {JhiAlertService, JhiEventManager} from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseService } from '../entities/exercise';
import { Course, CourseService } from '../entities/course';
import { ExerciseResultService } from '../entities/result/result.service';
import { DifferencePipe } from 'angular2-moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { JhiResultDetailComponent } from '../courses/results/result.component';
import { ModelingAssessmentService } from '../entities/modeling-assessment/modeling-assessment.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './assessment-dashboard.component.html',
    providers:  [
        JhiAlertService, ModelingAssessmentService
    ]
})

export class AssessmentDashboardComponent implements OnInit, OnDestroy {

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    predicate: any;
    reverse: any;
    nextOptimalSubmissionIds = [];
    showAllResults: string;
    results: Result[];
    allResults: Result[];
    eventSubscriber: Subscription;

    constructor(private route: ActivatedRoute,
                private momentDiff: DifferencePipe,
                private courseService: CourseService,
                private exerciseService: ExerciseService,
                private exerciseResultService: ExerciseResultService,
                private modelingAssessmentService: ModelingAssessmentService,
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
                this.getResults(true);
            });
        });
        this.registerChangeInCourses();
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', response => this.getResults(false));
    }

    getResults(forceReload: boolean) {
        this.exerciseResultService.query(this.exercise.course.id, this.exercise.id, {
            showAllResults: this.showAllResults,
            ratedOnly: false,
            withSubmissions: true,
            withAssessors: true
        }).subscribe((res: HttpResponse<Result[]>) => {
            const tempResults: Result[] = res.body;
            tempResults.forEach(function(result: Result) {
                result.participation.results = [result];
            });
            this.allResults = tempResults;
            this.filterResults(forceReload);
        });
    }

    filterResults(forceReload: boolean) {
        this.results = [];
            if (this.nextOptimalSubmissionIds.length < 3 || forceReload) {
                this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id).subscribe(optimal => {
                    this.nextOptimalSubmissionIds = optimal.body.map(submission => submission.id);
                    this.filterOptimal();
                });
            } else {
                this.filterOptimal();
            }
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

    filterOptimal() {
        this.allResults.forEach(result => {
            result.optimal = result.submission && this.nextOptimalSubmissionIds.includes(result.submission.id);
        });
        if (this.showAllResults === 'optimal') {
            this.results = this.allResults.filter(result => {
                return result.optimal;
            });
        }
    }

    durationString(completionDate, initializationDate) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(JhiResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
    }

    toggleShowAllResults(newValue) {
        this.showAllResults = newValue;
        this.filterResults(false);
    }

    refresh() {
        this.getResults(true);
    }

    resetOptimality() {
        this.modelingAssessmentService.resetOptimality(this.exercise.id).subscribe(() => {
            this.filterResults(true);
        });
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() { }
}
