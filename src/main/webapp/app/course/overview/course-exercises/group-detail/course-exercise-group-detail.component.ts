import { Component, DestroyRef, EnvironmentInjector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { DifficultyLevel, Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { forkJoin } from 'rxjs';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { NgClass } from '@angular/common';
import { InformationBox, InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { isDateLessThanAWeekInTheFuture } from 'app/foundation/util/date.utils';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [RouterLink, FaIconComponent, ArtemisDatePipe, ArtemisTimeAgoPipe, ExerciseHeadersInformationComponent, InformationBoxComponent, TooltipModule, NgClass],
    /* preserveWhitespaces: false is required here because the global tsconfig sets preserveWhitespaces: true,
     * which inserts whitespace text nodes that break [contentComponent] slot matching in jhi-information-box. */
    preserveWhitespaces: false,
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
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
    private readonly now = this.serverDateService.now();

    private readonly groupId = signal<number | undefined>(undefined);
    private readonly courseExercises = signal<Exercise[]>([]);
    protected readonly course = signal<Course | undefined>(undefined);

    private readonly problemStatements = signal<Map<number, string>>(new Map());
    private readonly problemStatementsRequested = new Set<number>();

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

    /** Sum of maxPoints across all exercises in the group. */
    protected readonly exerciseSumMaxPoints = computed<number>(() => this.exercises().reduce((sum, ex) => sum + (ex.maxPoints ?? 0), 0));

    /** Sum of achieved points across all exercises in the group, based on latest rated results. */
    protected readonly achievedGroupPoints = computed<number>(() => {
        const course = this.course();
        return this.exercises().reduce((sum, ex) => {
            const participation = this.exerciseParticipation(ex);
            const results = getAllResultsOfAllSubmissions(participation?.submissions);
            const latestRated = results.filter((r) => r.rated).sort((a, b) => (b.id ?? 0) - (a.id ?? 0))[0];
            if (!latestRated?.score || !ex.maxPoints) {
                return sum;
            }
            return sum + (roundValueSpecifiedByCourseSettings((latestRated.score * ex.maxPoints) / 100, course) ?? 0);
        }, 0);
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

        this.courseManagementService
            .findOneForDashboard(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    this.course.set(response.body ?? undefined);
                    this.courseExercises.set(response.body?.exercises ?? []);
                },
            });

        toObservable(this.group)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((g) => {
                if (g?.id !== undefined && g.title) {
                    this.entityTitleService.setTitle(EntityType.EXERCISE_VARIANT_GROUP, [g.id], g.title);
                }
            });

        effect(() => {
            const members = this.group()?.exercises ?? [];
            const missing = members.filter((exercise) => exercise.id !== undefined && exercise.problemStatement === undefined && !this.problemStatementsRequested.has(exercise.id));
            if (missing.length === 0) {
                return;
            }
            missing.forEach((exercise) => this.problemStatementsRequested.add(exercise.id!));
            forkJoin(missing.map((exercise) => this.exerciseService.getExerciseDetails(exercise.id!)))
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((responses) => {
                    const next = new Map(this.problemStatements());
                    for (const response of responses) {
                        const exercise = response.body?.exercise;
                        if (exercise?.id !== undefined && exercise.problemStatement !== undefined) {
                            next.set(exercise.id, exercise.problemStatement);
                        }
                    }
                    this.problemStatements.set(next);
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

    protected exerciseParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.[0];
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }
}
