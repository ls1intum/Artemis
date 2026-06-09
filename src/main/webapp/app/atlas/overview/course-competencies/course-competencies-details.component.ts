import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    ConfidenceReason,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
    getConfidence,
    getIcon,
    getMastery,
    getProgress,
} from 'app/atlas/shared/entities/competency.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { faEye, faLayerGroup, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { Subscription, combineLatest } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { FireworksComponent } from 'app/atlas/overview/fireworks/fireworks.component';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';
import { ButtonModule } from 'primeng/button';
import { CourseExerciseRowComponent } from 'app/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CompetencyDetailDevSettingsModalComponent } from './competency-detail-dev-settings-modal.component';
import { CompetencyDetailDevSettingsService } from './competency-detail-dev-settings.service';

interface CompetencyExerciseGroupDisplay {
    groupId: number;
    groupTitle: string;
    exercises: CompetencyExerciseLink[];
}

interface StandaloneExerciseItem {
    type: 'standalone';
    link: CompetencyExerciseLink;
}

interface GroupExerciseItem {
    type: 'group';
    group: CompetencyExerciseGroupDisplay;
}

type ExerciseDisplayItem = StandaloneExerciseItem | GroupExerciseItem;

@Component({
    selector: 'jhi-course-competencies-details',
    templateUrl: './course-competencies-details.component.html',
    styleUrls: ['../../../course/overview/course-overview/course-overview.scss', './course-competencies-details.component.scss'],
    imports: [
        FireworksComponent,
        TranslateDirective,
        FaIconComponent,
        NgbTooltip,
        NgClass,
        RouterLink,
        ExerciseUnitComponent,
        AttachmentVideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        CompetencyRingsComponent,
        SidePanelComponent,
        HelpIconComponent,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        HtmlForMarkdownPipe,
        ButtonModule,
        CourseExerciseRowComponent,
        CompetencyDetailDevSettingsModalComponent,
    ],
})
export class CourseCompetenciesDetailsComponent implements OnInit, OnDestroy {
    private courseStorageService = inject(CourseStorageService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);
    private courseCompetencyService = inject(CourseCompetencyService);
    private lectureUnitService = inject(LectureUnitService);
    private readonly scienceService = inject(ScienceService);

    protected readonly devSettings = inject(CompetencyDetailDevSettingsService);
    protected readonly settingsVisible = signal(false);

    competencyId?: number;
    course?: Course;
    courseId?: number;
    isLoading = false;
    competency: Competency;
    competencyProgress: CompetencyProgress;
    showFireworks = false;
    paramsSubscription: Subscription;

    groupedExerciseLinks: ExerciseDisplayItem[] = [];

    readonly LectureUnitType = LectureUnitType;
    protected readonly ConfidenceReason = ConfidenceReason;

    faPencilAlt = faPencilAlt;
    faLayerGroup = faLayerGroup;
    faEye = faEye;
    getIcon = getIcon;

    ngOnInit(): void {
        // example route looks like: /courses/1/competencies/10
        const courseIdParams$ = this.activatedRoute.parent?.parent?.params;
        const competencyIdParams$ = this.activatedRoute.params;

        if (courseIdParams$) {
            this.paramsSubscription = combineLatest([courseIdParams$, competencyIdParams$]).subscribe(([courseIdParams, competencyIdParams]) => {
                this.competencyId = Number(competencyIdParams.competencyId);
                this.courseId = Number(courseIdParams.courseId);
                this.course = this.courseStorageService.getCourse(this.courseId);
                if (this.competencyId && this.courseId) {
                    this.loadData();
                }

                this.scienceService.logEvent(ScienceEventType.COMPETENCY__OPEN, this.competencyId);
            });
        }
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
    }

    private loadData() {
        this.isLoading = true;

        this.courseCompetencyService.findById(this.competencyId!, this.courseId!).subscribe({
            next: (competencyResp) => {
                this.competency = competencyResp.body!;
                this.competencyProgress = this.getUserProgress();

                this.handleExerciseLinks();
                this.computeGroupedExerciseLinks();

                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Add exercises as lecture units for display in the default view.
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

    /**
     * Group exercise links by their groupId for Versions 2 and 3.
     * Exercises without a groupId are treated as standalone items.
     * @private
     */
    private computeGroupedExerciseLinks() {
        const links = this.competency.exerciseLinks ?? [];
        const groupsById = new Map<number, CompetencyExerciseGroupDisplay>();
        const result: ExerciseDisplayItem[] = [];

        for (const link of links) {
            if (link.groupId !== undefined && link.groupTitle !== undefined) {
                const existing = groupsById.get(link.groupId);
                if (existing) {
                    existing.exercises.push(link);
                } else {
                    const group: CompetencyExerciseGroupDisplay = { groupId: link.groupId, groupTitle: link.groupTitle, exercises: [link] };
                    groupsById.set(link.groupId, group);
                    result.push({ type: 'group', group });
                }
            } else {
                result.push({ type: 'standalone', link });
            }
        }

        this.groupedExerciseLinks = result;
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

    protected toExerciseUnit(link: CompetencyExerciseLink): ExerciseUnit {
        const unit = new ExerciseUnit();
        unit.id = link.exercise?.id;
        unit.exercise = link.exercise;
        return unit;
    }
}
