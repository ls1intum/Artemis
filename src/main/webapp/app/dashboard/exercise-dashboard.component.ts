import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseService, ExerciseType } from '../entities/exercise';
import { Course, CourseService } from '../entities/course';
import { ResultService } from '../entities/result/result.service';
import { DifferencePipe } from 'ngx-moment';
import { ParticipationService } from '../entities/participation/participation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../entities/result';
import { ResultDetailComponent } from '../entities/result/result-detail.component';
import { HttpResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-instructor-dashboard',
    templateUrl: './exercise-dashboard.component.html',
    providers: [JhiAlertService, ModelingAssessmentService, SourceTreeService],
})
export class ExerciseDashboardComponent implements OnInit, OnDestroy {
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

    constructor(
        private route: ActivatedRoute,
        private momentDiff: DifferencePipe,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private modelingAssessmentService: ModelingAssessmentService,
        private participationService: ParticipationService,
        private sourceTreeService: SourceTreeService,
        private modalService: NgbModal,
        private eventManager: JhiEventManager,
    ) {
        this.reverse = false;
        this.predicate = 'id';
        this.showAllResults = 'all';
        this.results = [];
        this.allResults = [];
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                this.getResults();
            });
        });
        this.registerChangeInCourses();
    }

    registerChangeInCourses() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', () => this.getResults());
    }

    getResults() {
        this.resultService
            .getResultsForExercise(this.exercise.course!.id, this.exercise.id, {
                showAllResults: this.showAllResults,
                ratedOnly: true,
                withSubmissions: this.exercise.type === ExerciseType.MODELING,
                withAssessors: this.exercise.type === ExerciseType.MODELING,
            })
            .subscribe((res: HttpResponse<Result[]>) => {
                const tempResults: Result[] = res.body!;
                tempResults.forEach(result => {
                    result.participation!.results = [result];
                    (result.participation! as StudentParticipation).exercise = this.exercise;
                    result.durationInMinutes = this.durationInMinutes(
                        result.completionDate!,
                        result.participation!.initializationDate ? result.participation!.initializationDate : this.exercise.releaseDate!,
                    );
                });
                this.allResults = tempResults;
                this.filterResults();
            });
    }

    filterResults() {
        this.results = [];
        if (this.showAllResults === 'successful') {
            this.results = this.allResults.filter(result => result.successful);
        } else if (this.showAllResults === 'unsuccessful') {
            this.results = this.allResults.filter(result => !result.successful);
        } else if (this.showAllResults === 'all') {
            this.results = this.allResults;
        }
    }

    durationInMinutes(completionDate: Moment, initializationDate: Moment) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    goToBuildPlan(result: Result) {
        this.sourceTreeService.goToBuildPlan(result.participation!);
    }

    goToRepository(result: Result) {
        window.open((result.participation! as ProgrammingExerciseStudentParticipation).repositoryUrl);
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
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
                const studentParticipation = result.participation! as StudentParticipation;
                let studentName = studentParticipation.student.firstName!;
                if (studentParticipation.student.lastName != null && studentParticipation.student.lastName !== '') {
                    studentName = studentName + ' ' + studentParticipation.student.lastName;
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
                const studentParticipation = result.participation! as StudentParticipation;
                let studentName = studentParticipation.student.firstName!;
                if (studentParticipation.student.lastName != null && studentParticipation.student.lastName !== '') {
                    studentName = studentName + ' ' + studentParticipation.student.lastName;
                }
                const studentId = studentParticipation.student.login;
                const score = result.score;

                if (index === 0) {
                    if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score');
                    } else {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score, Repo Link');
                    }
                }
                if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                    rows.push(studentName + ', ' + studentId + ', ' + score);
                } else {
                    const repoLink = (studentParticipation as ProgrammingExerciseStudentParticipation).repositoryUrl;
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

    callback() {}
}
