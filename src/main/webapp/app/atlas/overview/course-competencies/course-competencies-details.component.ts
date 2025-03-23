import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
    Competency,
    CompetencyJol,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    ConfidenceReason,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
    getConfidence,
    getIcon,
    getMastery,
    getProgress,
} from 'app/atlas/shared/entities/competency.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/course-lecture-details.component';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/lectureUnit.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { Observable, Subscription, combineLatest, forkJoin } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/core/course/manage/course-storage.service';
import { Course } from 'app/core/shared/entities/course.model';
import { CourseCompetencyService } from 'app/atlas/shared/course-competency.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentUnitComponent } from 'app/lecture/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/lecture/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { FireworksComponent } from 'app/atlas/overview/fireworks/fireworks.component';

@Component({
    selector: 'jhi-course-competencies-details',
    templateUrl: './course-competencies-details.component.html',
    styleUrls: ['../../../core/course/overview/course-overview.scss'],
    imports: [
        FireworksComponent,
        TranslateDirective,
        FaIconComponent,
        NgbTooltip,
        NgClass,
        RouterLink,
        ExerciseUnitComponent,
        AttachmentUnitComponent,
        VideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        CompetencyRingsComponent,
        SidePanelComponent,
        HelpIconComponent,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        HtmlForMarkdownPipe,
    ],
})
export class CourseCompetenciesDetailsComponent implements OnInit, OnDestroy {
    private featureToggleService = inject(FeatureToggleService);
    private courseStorageService = inject(CourseStorageService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);
    private courseCompetencyService = inject(CourseCompetencyService);
    private lectureUnitService = inject(LectureUnitService);

    competencyId?: number;
    course?: Course;
    courseId?: number;
    isLoading = false;
    competency: Competency;
    competencyProgress: CompetencyProgress;
    judgementOfLearning: CompetencyJol | undefined;
    promptForJolRating = false;
    showFireworks = false;
    dashboardFeatureActive = false;
    paramsSubscription: Subscription;

    readonly LectureUnitType = LectureUnitType;
    protected readonly ConfidenceReason = ConfidenceReason;

    faPencilAlt = faPencilAlt;
    getIcon = getIcon;

    ngOnInit(): void {
        // example route looks like: /courses/1/competencies/10
        const courseIdParams$ = this.activatedRoute.parent?.parent?.params;
        const competencyIdParams$ = this.activatedRoute.params;
        const dashboardFeatureToggleActive$ = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard);

        if (courseIdParams$) {
            this.paramsSubscription = combineLatest([courseIdParams$, competencyIdParams$, dashboardFeatureToggleActive$]).subscribe(
                ([courseIdParams, competencyIdParams, dashboardFeatureActive]) => {
                    this.competencyId = Number(competencyIdParams.competencyId);
                    this.courseId = Number(courseIdParams.courseId);
                    this.dashboardFeatureActive = dashboardFeatureActive;
                    this.course = this.courseStorageService.getCourse(this.courseId);
                    if (this.competencyId && this.courseId) {
                        this.loadData();
                    }
                },
            );
        }
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
    }

    private loadData() {
        this.isLoading = true;

        const observables = [this.courseCompetencyService.findById(this.competencyId!, this.courseId!)] as Observable<
            HttpResponse<Competency | Competency[] | { current: CompetencyJol; prior?: CompetencyJol }>
        >[];

        if (this.judgementOfLearningEnabled) {
            observables.push(this.courseCompetencyService.getAllForCourse(this.courseId!));
            observables.push(this.courseCompetencyService.getJoL(this.courseId!, this.competencyId!));
        }

        forkJoin(observables).subscribe({
            next: ([competencyResp, courseCompetenciesResp, judgementOfLearningResp]) => {
                this.competency = competencyResp.body! as Competency;
                this.competencyProgress = this.getUserProgress();

                if (this.judgementOfLearningEnabled) {
                    const competencies = courseCompetenciesResp.body! as Competency[];
                    const progress = this.competency.userProgress?.first();
                    this.promptForJolRating = CompetencyJol.shouldPromptForJol(this.competency, progress, competencies);
                    const judgementOfLearning = (judgementOfLearningResp?.body ?? undefined) as { current: CompetencyJol; prior?: CompetencyJol } | undefined;
                    if (
                        !judgementOfLearning?.current ||
                        judgementOfLearning.current.competencyProgress !== (progress?.progress ?? 0) ||
                        judgementOfLearning.current.competencyConfidence !== (progress?.confidence ?? 1)
                    ) {
                        this.judgementOfLearning = undefined;
                    } else {
                        this.judgementOfLearning = judgementOfLearning?.current;
                    }
                }

                this.handleExerciseLinks();

                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Add exercises as lecture units for display
     * @private
     */
    private handleExerciseLinks() {
        if (this.competency.exerciseLinks) {
            this.competency.lectureUnitLinks = this.competency.lectureUnitLinks ?? [];
            this.competency.lectureUnitLinks.push(
                ...this.competency.exerciseLinks.map((exerciseLink) => {
                    const exerciseUnit = new ExerciseUnit();
                    exerciseUnit.id = exerciseLink.exercise?.id;
                    exerciseUnit.exercise = exerciseLink.exercise;
                    return new CompetencyLectureUnitLink(this.competency, exerciseUnit as LectureUnit, MEDIUM_COMPETENCY_LINK_WEIGHT);
                }),
            );
        }
    }

    showFireworksIfMastered() {
        if (this.mastery >= 100 && !this.showFireworks) {
            setTimeout(() => (this.showFireworks = true), 1000);
            setTimeout(() => (this.showFireworks = false), 6000);
        }
    }

    getUserProgress(): CompetencyProgress {
        if (this.competency.userProgress?.length) {
            return this.competency.userProgress.first()!;
        }
        return { progress: 0, confidence: 1 } as CompetencyProgress;
    }

    get progress(): number {
        return getProgress(this.competencyProgress);
    }

    get confidence(): number {
        return getConfidence(this.competencyProgress);
    }

    get mastery(): number {
        return getMastery(this.competencyProgress);
    }

    get isMastered(): boolean {
        return this.mastery >= 100;
    }

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        if (!event.lectureUnit.lecture || !event.lectureUnit.visibleToStudents || event.lectureUnit.completed === event.completed) {
            return;
        }

        this.lectureUnitService.setCompletion(event.lectureUnit.id!, event.lectureUnit.lecture!.id!, event.completed).subscribe({
            next: () => {
                event.lectureUnit.completed = event.completed;

                this.courseCompetencyService.getProgress(this.competencyId!, this.courseId!, true).subscribe({
                    next: (resp) => {
                        this.competency.userProgress = [resp.body!];
                        this.showFireworksIfMastered();
                    },
                });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    get softDueDatePassed(): boolean {
        return dayjs().isAfter(this.competency.softDueDate);
    }

    get judgementOfLearningEnabled() {
        return (this.course?.studentCourseAnalyticsDashboardEnabled ?? false) && this.dashboardFeatureActive;
    }
}
