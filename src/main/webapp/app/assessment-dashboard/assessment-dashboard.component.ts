import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { Exercise, ExerciseService } from '../entities/exercise';
import { Course, CourseService } from '../entities/course';
import { ExerciseResultService } from '../entities/result/result.service';
import { DifferencePipe } from 'angular2-moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { ResultDetailComponent } from '../entities/result/result.component';
import { ModelingAssessmentService } from '../entities/modeling-assessment/modeling-assessment.service';
import { HttpResponse } from '@angular/common/http';
import { Principal } from '../shared';

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
    results: Result[];
    allResults: Result[];
    optimalResults: Result[];
    eventSubscriber: Subscription;
    assessedResults: number;
    allSubmissionsVisible: boolean;
    busy: boolean;
    accountId: number;
    isAuthorized: boolean;

    constructor(private route: ActivatedRoute,
                private jhiAlertService: JhiAlertService,
                private router: Router,
                private momentDiff: DifferencePipe,
                private courseService: CourseService,
                private exerciseService: ExerciseService,
                private exerciseResultService: ExerciseResultService,
                private modelingAssessmentService: ModelingAssessmentService,
                private modalService: NgbModal,
                private eventManager: JhiEventManager,
                private principal: Principal) {
        this.reverse = false;
        this.predicate = 'id';
        this.results = [];
        this.allResults = [];
        this.optimalResults = [];
        this.isAuthorized = this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    }

    ngOnInit() {
        this.principal.identity().then(account => {
            this.accountId = account.id;
        });
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                this.exercise = res.body;
                this.getResults(true);
            });
        });
        this.registerChangeInResults();
    }

    registerChangeInResults() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getResults(true));
    }

    getResults(forceReload: boolean) {
        this.exerciseResultService.query(this.exercise.course.id, this.exercise.id, {
            showAllResults: 'all',
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
            this.assessedResults = this.allResults.filter(result => result.rated).length;
        });
    }

    filterResults(forceReload: boolean) {
        this.results = [];
        if (this.nextOptimalSubmissionIds.length < 3 || forceReload) {
            this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id).subscribe(optimal => {
                this.nextOptimalSubmissionIds = optimal.body.map(submission => submission.id);
                this.applyFilter();
            });
        } else {
            this.applyFilter();
        }
    }

    applyFilter() {
        // A result is optimal if it is part of nextOptimalSubmissionIds and nobody is currently assessing it or you are currently assessing it
        this.allResults.forEach(result => {
            result.optimal = result.submission && ((this.nextOptimalSubmissionIds.includes(result.submission.id) && !result.assessor) ||
                (result.assessor != null && !result.rated));
        });
        this.optimalResults = this.allResults.filter(result => {
            return result.optimal;
        });
        this.results = this.allResults.filter(result => {
            return !result.optimal;
        });
    }

    durationString(completionDate, initializationDate) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(ResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
    }

    refresh() {
        this.getResults(true);
    }

    resetOptimality() {
        this.modelingAssessmentService.resetOptimality(this.exercise.id).subscribe(() => {
            this.filterResults(true);
        });
    }

    makeAllSubmissionsVisible() {
        if (!this.busy) {
            this.allSubmissionsVisible = true;
        }
    }

    assessNextOptimal(attempts) {
        if (attempts > 3) {
            this.busy = false;
            this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
            return;
        }
        this.busy = true;
        if (this.nextOptimalSubmissionIds.length === 0) {
            setTimeout(() => {
                this.modelingAssessmentService.getOptimalSubmissions(this.exercise.id).subscribe(optimal => {
                    this.nextOptimalSubmissionIds = optimal.body.map(submission => submission.id);
                    this.assessNextOptimal(attempts + 1);
                });
            }, 500 + 1000 * attempts);
        } else {
            const randomInt = Math.floor(Math.random() * (this.nextOptimalSubmissionIds.length));
            this.router.navigate(['apollon-diagrams', 'exercise', this.exercise.id, this.nextOptimalSubmissionIds[randomInt], 'tutor']);
        }
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    callback() { }
}
