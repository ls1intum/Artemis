import { ChangeDetectionStrategy, Component, DestroyRef, EnvironmentInjector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { DifficultyLevel, Exercise, IncludedInOverallScore, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
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
    private readonly exerciseVariantGroupService = inject(ExerciseVariantGroupService);
    private readonly entityTitleService = inject(EntityTitleService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly plantUmlWrapper = inject(ProgrammingExercisePlantUmlExtensionWrapper);
    private readonly sanitizer = inject(DomSanitizer);
    private readonly injector = inject(EnvironmentInjector);

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

    private readonly problemStatements = signal<Map<number, string>>(new Map());
    /** Groups whose member previews have already been requested, so revisiting a group does not re-fetch them. */
    private readonly requestedGroupIds = new Set<number>();

    protected readonly renderedStatements = signal<Map<number, SafeHtml>>(new Map());
    private plantUmlCallbacks: Array<() => void> = [];

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

        this.plantUmlWrapper
            .subscribeForInjectableElementsFound()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((cb) => this.plantUmlCallbacks.push(cb));

        effect(() => {
            const exercises = this.exercises();
            const statements = this.problemStatements();
            untracked(() => this.renderProblemStatements(exercises, statements));
        });

        // The course itself is already loaded by the course overview container this route lives in; the only field read
        // from it here is maxComplaintTimeDays, via the exercise header.
        this.course.set(this.courseStorageService.getCourse(this.courseId));
        this.courseStorageService
            .subscribeToCourseUpdates(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((course) => this.course.set(course));

        // The exercises come from the same response the exercises tab uses, so arriving here from a group card costs no
        // request at all and a deep link costs exactly one. It also populates the achieved variant group points that
        // {@link achievedGroupPoints} reads out of the ScoresStorageService.
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

        effect(() => {
            const group = this.group();
            const groupId = group?.id;
            if (group === undefined || groupId === undefined || this.requestedGroupIds.has(groupId)) {
                return;
            }
            // The dashboard strips problem statements to stay small, so any member missing one needs the batch preview
            // request. When every member already carries its statement (e.g. inlined by a caller), there is nothing to do.
            const needsPreview = (group.exercises ?? []).some((exercise) => exercise.id !== undefined && exercise.problemStatement === undefined);
            if (!needsPreview) {
                return;
            }
            this.requestedGroupIds.add(groupId);
            // One lightweight batch request for the whole group instead of one heavyweight exercise-details request per member.
            this.exerciseVariantGroupService
                .getProblemStatements(this.courseId, groupId)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: (previews) => {
                        const next = new Map(this.problemStatements());
                        for (const preview of previews) {
                            if (preview.problemStatement !== undefined) {
                                next.set(preview.exerciseId, preview.problemStatement);
                            }
                        }
                        this.problemStatements.set(next);
                    },
                    error: () => {
                        // The group was optimistically marked as requested; release it so a later change (or revisit)
                        // can retry the batch instead of leaving the previews permanently blocked.
                        this.requestedGroupIds.delete(groupId);
                    },
                });
        });
    }

    private renderProblemStatements(exercises: Exercise[], statements: Map<number, string>): void {
        this.plantUmlCallbacks = [];
        const map = new Map<number, SafeHtml>();

        for (const exercise of exercises) {
            if (exercise.id === undefined) continue;
            const ps = exercise.problemStatement ?? statements.get(exercise.id);
            if (!ps) continue;

            // Strip task syntax — [task][Name](tests) → Name — so it renders as plain text instead of a link.
            const preprocessed = ps.replace(taskRegex, (_match, name: string) => name);
            this.plantUmlWrapper.setExerciseId(exercise.id);
            const html = htmlForMarkdown(preprocessed, [this.plantUmlWrapper.getExtension()]);
            map.set(exercise.id, this.sanitizer.bypassSecurityTrustHtml(html));
        }

        this.renderedStatements.set(map);

        afterNextRender(
            () => {
                this.plantUmlCallbacks.forEach((cb) => cb());
                this.plantUmlCallbacks = [];
            },
            { injector: this.injector },
        );
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
