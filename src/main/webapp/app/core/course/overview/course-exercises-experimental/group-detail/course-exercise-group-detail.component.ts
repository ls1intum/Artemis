import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { DifficultyLevel, Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockDataService } from 'app/core/interceptor/mock-data.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { forkJoin } from 'rxjs';

/**
 * Detail page for an exercise with several variants, shown in the experimental student view's right
 * pane. Explains that the exercise has multiple variants and lists them read-only (each variant links
 * to its own exercise page). Dev-only mock — nothing is persisted.
 */
@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [RouterLink, NgTemplateOutlet, SlicePipe, FaIconComponent, ArtemisDatePipe],
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly mockDataService = inject(MockDataService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly getIcon = getIcon;
    protected readonly DifficultyLevel = DifficultyLevel;

    // The group id from the route, and (real mode only) the course's release-filtered exercises from
    // the dashboard. The resolved group is derived from these: mock groups in mock mode, otherwise the
    // group reconstructed from the exercises that carry the matching embedded variant-group reference.
    private readonly groupId = signal<number | undefined>(undefined);
    private readonly courseExercises = signal<Exercise[]>([]);

    // The dashboard trims exercise problem statements, so for real data they are fetched per variant
    // (via the exercise /details endpoint) and looked up here by exercise id to render the preview.
    private readonly problemStatements = signal<Map<number, string>>(new Map());
    private readonly problemStatementsRequested = new Set<number>();

    protected readonly group = computed<CourseExerciseGroup | undefined>(() => {
        const groupId = this.groupId();
        if (groupId === undefined) {
            return undefined;
        }
        const groups = this.mockDataService.enabled() ? this.mockService.getGroups() : buildGroupsFromExercises(this.courseExercises());
        return groups.find((candidate) => candidate.id === groupId);
    });
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);

    // Problem-statement preview is fixed at 2 lines; slice end guarantees enough characters.
    protected readonly previewSliceEnd = 403;

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.groupId.set(Number(params['groupId'])));

        // With real data the group is reconstructed from the dashboard's exercises (which are already
        // filtered for the student); mock mode resolves the group synchronously from the mock service.
        if (!this.mockDataService.enabled()) {
            this.courseManagementService
                .findOneForDashboard(this.courseId)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({ next: (response) => this.courseExercises.set(response.body?.exercises ?? []) });
        }

        // The dashboard omits problem statements; with real data, fetch each variant's details once the
        // group is resolved so the preview can be shown (mock exercises already carry their statement).
        effect(() => {
            if (this.mockDataService.enabled()) {
                return;
            }
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

    /**
     * Preview text for a variant's problem statement: the embedded statement (mock) or the separately
     * fetched one (real), with a leading markdown heading (e.g. "## Title") stripped so the preview
     * starts at the first real word regardless of the source's formatting.
     */
    protected problemStatementPreview(exercise: Exercise): string | undefined {
        const statement = exercise.problemStatement ?? (exercise.id !== undefined ? this.problemStatements().get(exercise.id) : undefined);
        return statement?.replace(/^\s*#{1,6}\s*/, '');
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }
}
