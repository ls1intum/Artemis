import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { DifficultyLevel, Exercise, IncludedInOverallScore, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { InformationBox, InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { isDateLessThanAWeekInTheFuture } from 'app/foundation/util/date.utils';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [
        RouterLink,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTimeAgoPipe,
        ArtemisTranslatePipe,
        TranslateDirective,
        ExerciseHeadersInformationComponent,
        InformationBoxComponent,
        TumUiTooltipDirective,
    ],
    /* preserveWhitespaces: false is required here because the global tsconfig sets preserveWhitespaces: true,
     * which inserts whitespace text nodes that break [contentComponent] slot matching in jhi-information-box. */
    preserveWhitespaces: false,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly courseOverviewExercisesService = inject(CourseOverviewExercisesService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly entityTitleService = inject(EntityTitleService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly getIcon = getIcon;
    protected readonly DifficultyLevel = DifficultyLevel;

    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly scoresStorageService = inject(ScoresStorageService);
    private readonly participationService = inject(ParticipationService);
    private readonly now = this.serverDateService.now();

    private readonly groupId = signal<number | undefined>(undefined);
    private readonly courseExercises = signal<Exercise[]>([]);
    protected readonly course = signal<Course | undefined>(undefined);

    protected readonly group = computed<CourseExerciseGroup | undefined>(() => {
        const groupId = this.groupId();
        if (groupId === undefined) {
            return undefined;
        }
        return buildGroupsFromExercises(this.courseExercises()).find((candidate) => candidate.id === groupId);
    });
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);

    /**
     * Sum of maxPoints over the INCLUDED_COMPLETELY variants only, matching the server's inclusion rule; bonus and
     * not-included variants must not inflate the denominator.
     */
    protected readonly exerciseSumMaxPoints = computed<number>(() =>
        this.exercises()
            .filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY)
            .reduce((sum, ex) => sum + (ex.maxPoints ?? 0), 0),
    );

    /**
     * The group's real maximum: min(sum of variant max points, group cap), since a cap above the sum cannot raise
     * what is achievable. The cap is detected with an explicit undefined check so a zero cap still applies.
     */
    protected readonly effectiveGroupMaxPoints = computed<number>(() => {
        const cap = this.group()?.maxPoints;
        const sum = this.exerciseSumMaxPoints();
        return cap !== undefined ? Math.min(sum, cap) : sum;
    });

    /** Whether the cap actually reduces the achievable maximum (set and strictly below the variants' sum). Only then is
     * the cap explanation (tooltip / callout) meaningful; a cap ≥ the sum behaves exactly like no cap. */
    protected readonly capReducesMaxPoints = computed<boolean>(() => {
        const cap = this.group()?.maxPoints;
        return cap !== undefined && cap < this.exerciseSumMaxPoints();
    });

    /**
     * The student's group points, taken from the authoritative server value via {@link ScoresStorageService}, which is
     * already capped and plagiarism-adjusted. Falls back to 0 until the dashboard scores load.
     */
    protected readonly achievedGroupPoints = computed<number>(() => {
        const group = this.group();
        if (group?.id === undefined) {
            return 0;
        }
        return this.scoresStorageService.getStoredAchievedGroupPoints(this.courseId, group.id) ?? 0;
    });

    protected readonly pointsInfoBoxData = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.points',
        content: { type: 'string', value: '' },
        isContentComponent: true,
    }));

    protected readonly variantsInfoBoxData = computed<InformationBox>(() => ({
        title: 'artemisApp.exerciseVariantGroup.detail.variants',
        content: { type: 'string', value: this.exercises().length },
    }));

    /** Dynamic date info boxes for the group header, mirroring the exercise due-date + assessment-due logic. */
    protected readonly groupDateInfoBoxes = computed<InformationBox[]>(() => {
        const group = this.group();
        const now = this.now;
        const dueDate = group?.dueDate;
        const startDate = group?.startDate;
        const assessmentDueDate = group?.assessmentDueDate;
        const items: InformationBox[] = [];

        if (dueDate) {
            if (dueDate.isBefore(now)) {
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
                    content: { type: 'dateTime', value: dueDate },
                    isContentComponent: true,
                });
            } else {
                const relative = isDateLessThanAWeekInTheFuture(dueDate, now);
                const color = dueDate.isBetween(now, now.add(1, 'day')) ? 'danger' : 'body-color';
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
                    content: { type: relative ? 'timeAgo' : 'dateTime', value: dueDate },
                    isContentComponent: true,
                    contentColor: color,
                    tooltip: relative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
                    tooltipParams: relative ? { date: dueDate.format('lll') } : undefined,
                });
            }
            if (dueDate.isBefore(now) && assessmentDueDate?.isAfter(now)) {
                items.push({
                    title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                    content: { type: 'dateTime', value: assessmentDueDate },
                    isContentComponent: true,
                    tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
                    tooltipParams: { date: assessmentDueDate.format('lll') },
                });
            }
        }
        if (startDate?.isAfter(now)) {
            const relative = isDateLessThanAWeekInTheFuture(startDate, now);
            items.push({
                title: 'artemisApp.courseOverview.exerciseDetails.startDate',
                content: { type: relative ? 'timeAgo' : 'dateTime', value: startDate },
                isContentComponent: true,
            });
        }

        return items;
    });

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.groupId.set(Number(params['groupId'])));

        // The course itself is already loaded by the course overview container this route lives in; the only field read
        // from it here is maxComplaintTimeDays, via the exercise header.
        this.course.set(this.courseStorageService.getCourse(this.courseId));
        this.courseStorageService
            .subscribeToCourseUpdates(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((course) => this.course.set(course));

        // The exercises come from the same endpoint the exercises tab uses, freshly for this navigation, and the same
        // response populates the achieved variant group points that {@link achievedGroupPoints} reads out of the
        // ScoresStorageService.
        this.courseOverviewExercisesService
            .loadIfNeeded(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (overview) => this.courseExercises.set(overview.exercises ?? []),
            });

        toObservable(this.group)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((g) => {
                if (g?.id !== undefined && g.title) {
                    this.entityTitleService.setTitle(EntityType.EXERCISE_VARIANT_GROUP, [g.id], g.title);
                }
            });
    }

    /**
     * The graded participation for a variant. The dashboard also returns practice runs in unspecified order, so the
     * first entry could otherwise show practice points on the card.
     */
    protected exerciseParticipation(exercise: Exercise): StudentParticipation | undefined {
        return this.participationService.getSpecificStudentParticipation(exercise.studentParticipations ?? [], false);
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }
}
