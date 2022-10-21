import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { AlertService } from 'app/core/util/alert.service';
import { LearningGoal, LearningGoalRelation } from 'app/entities/learningGoal.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, finalize, map, switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { forkJoin, Subject } from 'rxjs';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';
import { captureException } from '@sentry/browser';
import { isEqual } from 'lodash-es';
import { faPencilAlt, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteImportComponent } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { ClusterNode, Edge, Node } from '@swimlane/ngx-graph';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styleUrls: ['./learning-goal-management.component.scss'],
})
export class LearningGoalManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    learningGoals: LearningGoal[] = [];
    prerequisites: LearningGoal[] = [];
    learningGoalIdToLearningGoalCourseProgress = new Map<number, CourseLearningGoalProgress>();
    // this is calculated using the participant scores table on the server instead of going participation -> submission -> result
    // we calculate it here to find out if the participant scores table is robust enough to replace the classic way of finding the last result
    learningGoalIdToLearningGoalCourseProgressUsingParticipantScoresTables = new Map<number, CourseLearningGoalProgress>();

    showRelations = false;
    tailLearningGoal?: number;
    headLearningGoal?: number;
    relationType?: string;
    nodes: Node[] = [];
    edges: Edge[] = [];
    clusters: ClusterNode[] = [];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private learningGoalService: LearningGoalService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.showRelations = this.accountService.isAdmin(); // beta feature
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    deleteLearningGoal(learningGoalId: number) {
        this.learningGoalService.delete(learningGoalId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    removePrerequisite(learningGoalId: number) {
        this.learningGoalService.removePrerequisite(learningGoalId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    getLearningGoalCourseProgress(learningGoal: LearningGoal) {
        return this.learningGoalIdToLearningGoalCourseProgress.get(learningGoal.id!);
    }

    loadData() {
        this.isLoading = true;
        this.learningGoalService
            .getAllPrerequisitesForCourse(this.courseId)
            .pipe(map((response: HttpResponse<LearningGoal[]>) => response.body!))
            .subscribe({
                next: (learningGoals) => {
                    this.prerequisites = learningGoals;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        this.learningGoalService
            .getAllForCourse(this.courseId)
            .pipe(
                switchMap((res) => {
                    this.learningGoals = res.body!;

                    this.nodes = this.learningGoals.map(
                        (learningGoal): Node => ({
                            id: `${learningGoal.id}`,
                            label: learningGoal.title,
                        }),
                    );

                    const relationsObservable = this.learningGoals.map((lg) => {
                        return this.learningGoalService.getLearningGoalRelations(lg.id!, this.courseId);
                    });

                    const progressObservable = this.learningGoals.map((lg) => {
                        return this.learningGoalService.getCourseProgress(lg.id!, this.courseId, false);
                    });

                    const progressObservableUsingParticipantScore = this.learningGoals.map((lg) => {
                        return this.learningGoalService.getCourseProgress(lg.id!, this.courseId, true);
                    });

                    return forkJoin([forkJoin(relationsObservable), forkJoin(progressObservable), forkJoin(progressObservableUsingParticipantScore)]);
                }),
            )
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([learningGoalRelations, learningGoalProgressResponses, learningGoalProgressResponsesUsingParticipantScores]) => {
                    const relations = [
                        ...learningGoalRelations
                            .flatMap((response) => response.body!)
                            .reduce((a, c) => {
                                a.set(c.id, c);
                                return a;
                            }, new Map())
                            .values(),
                    ];
                    this.edges = relations.map(
                        (relation): Edge => ({
                            id: `edge${relation.id}`,
                            source: `${relation.tailLearningGoal?.id}`,
                            target: `${relation.headLearningGoal?.id}`,
                            label: relation.type,
                            data: {
                                id: relation.id,
                            },
                        }),
                    );
                    this.clusters = relations
                        .filter((relation) => relation.type === 'CONSECUTIVE')
                        .map(
                            (relation): ClusterNode => ({
                                id: `cluster${relation.id}`,
                                label: relation.type,
                                childNodeIds: [`${relation.tailLearningGoal?.id}`, `${relation.headLearningGoal?.id}`],
                                data: {
                                    id: relation.id,
                                },
                            }),
                        );

                    for (const learningGoalProgressResponse of learningGoalProgressResponses) {
                        const learningGoalProgress = learningGoalProgressResponse.body!;
                        this.learningGoalIdToLearningGoalCourseProgress.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                    }
                    for (const learningGoalProgressResponse of learningGoalProgressResponsesUsingParticipantScores) {
                        const learningGoalProgress = learningGoalProgressResponse.body!;
                        this.learningGoalIdToLearningGoalCourseProgressUsingParticipantScoresTables.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                    }
                    this.testIfScoreUsingParticipantScoresTableDiffers();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    openImportModal() {
        const modalRef = this.modalService.open(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.disabledIds = this.learningGoals.concat(this.prerequisites).map((learningGoal) => learningGoal.id);
        modalRef.result.then(
            (result: LearningGoal) => {
                this.learningGoalService
                    .addPrerequisite(result.id!, this.courseId)
                    .pipe(
                        filter((res: HttpResponse<LearningGoal>) => res.ok),
                        map((res: HttpResponse<LearningGoal>) => res.body),
                    )
                    .subscribe({
                        next: (res: LearningGoal) => {
                            this.prerequisites.push(res);
                        },
                        error: (res: HttpErrorResponse) => onError(this.alertService, res),
                    });
            },
            () => {},
        );
    }

    createRelation() {
        this.learningGoalService
            .createLearningGoalRelation(this.tailLearningGoal!, this.headLearningGoal!, this.relationType!, this.courseId)
            .pipe(
                filter((res: HttpResponse<LearningGoalRelation>) => res.ok),
                map((res: HttpResponse<LearningGoalRelation>) => res.body),
            )
            .subscribe({
                next: () => {
                    this.loadData();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    removeRelation(edge: Edge) {
        this.learningGoalService.removeLearningGoalRelation(Number(edge.source), Number(edge.data.id), this.courseId).subscribe({
            next: () => {
                this.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Using this test we want to find out if the progress calculation using the participant scores table leads to the same
     * result as going through participation -> submission -> result
     */
    private testIfScoreUsingParticipantScoresTableDiffers() {
        this.learningGoalIdToLearningGoalCourseProgress.forEach((learningGoalProgress, learningGoalId) => {
            const learningGoalProgressParticipantScoresTable = this.learningGoalIdToLearningGoalCourseProgressUsingParticipantScoresTables.get(learningGoalId);
            if (
                !isEqual(
                    learningGoalProgress.averagePointsAchievedByStudentInLearningGoal,
                    learningGoalProgressParticipantScoresTable!.averagePointsAchievedByStudentInLearningGoal,
                )
            ) {
                const message = `Warning: Learning Goal(id=${learningGoalProgress.learningGoalId}) Course Progress different using participant scores for course ${
                    this.courseId
                }! Original: ${JSON.stringify(learningGoalProgress)} | Using ParticipantScores: ${JSON.stringify(learningGoalProgressParticipantScoresTable)}!`;
                captureException(new Error(message));
            }
        });
    }
}
