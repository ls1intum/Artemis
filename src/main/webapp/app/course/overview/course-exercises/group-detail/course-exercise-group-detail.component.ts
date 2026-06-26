import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { DifficultyLevel, Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { forkJoin } from 'rxjs';

@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [RouterLink, NgTemplateOutlet, SlicePipe, FaIconComponent, ArtemisDatePipe],
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly entityTitleService = inject(EntityTitleService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly getIcon = getIcon;
    protected readonly DifficultyLevel = DifficultyLevel;

    private readonly groupId = signal<number | undefined>(undefined);
    private readonly courseExercises = signal<Exercise[]>([]);

    private readonly problemStatements = signal<Map<number, string>>(new Map());
    private readonly problemStatementsRequested = new Set<number>();

    protected readonly group = computed<CourseExerciseGroup | undefined>(() => {
        const groupId = this.groupId();
        if (groupId === undefined) {
            return undefined;
        }
        return buildGroupsFromExercises(this.courseExercises()).find((candidate) => candidate.id === groupId);
    });
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);

    protected readonly previewSliceEnd = 403;

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.groupId.set(Number(params['groupId'])));

        this.courseManagementService
            .findOneForDashboard(this.courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: (response) => this.courseExercises.set(response.body?.exercises ?? []) });

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

    protected problemStatementPreview(exercise: Exercise): string | undefined {
        const statement = exercise.problemStatement ?? (exercise.id !== undefined ? this.problemStatements().get(exercise.id) : undefined);
        return statement?.replace(/^\s*#{1,6}\s*/, '');
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }
}
