import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faLayerGroup, faPaperPlane, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Checkbox } from 'primeng/checkbox';
import { DifficultyLevel, Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, handInLimitFor } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { GroupHandInSelectionService } from '../group-hand-in-selection.service';

/**
 * Detail page for an exercise with several variants, shown in the experimental student view's right
 * pane. Explains that the exercise has multiple variants and lets the student pick which variants
 * should count towards their score (read-only facts otherwise). Dev-only mock — nothing is persisted.
 */
@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [FormsModule, RouterLink, NgTemplateOutlet, SlicePipe, FaIconComponent, Checkbox, ExerciseActionButtonComponent, ArtemisDatePipe],
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly selectionService = inject(GroupHandInSelectionService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faCheck = faCheck;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly getIcon = getIcon;
    protected readonly DifficultyLevel = DifficultyLevel;

    protected readonly group = signal<CourseExerciseGroup | undefined>(undefined);
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);

    // Problem-statement preview is fixed at 2 lines; slice end guarantees enough characters.
    protected readonly previewSliceEnd = 403;

    // How many of the group's exercises count towards the score (read-only); caps the selection.
    protected readonly countTowardsScore = computed(() => handInLimitFor(this.group()));

    // The only thing the student can change: which exercises they want assessed.
    protected readonly selectedExercises = signal<Exercise[]>([]);

    // How many exercises have actually been handed in (submitted) for this group; mirrors the sidebar.
    protected readonly handedInCount = computed(() => {
        const groupId = this.group()?.id;
        return groupId !== undefined ? this.selectionService.getSubmittedSelection(groupId).length : 0;
    });

    // Track which selection has been submitted, so the submit button only enables on unsubmitted changes.
    private readonly submittedSelectionKey = signal('');
    private readonly currentSelectionKey = computed(() => this.keyForIds(this.selectedIds()));
    protected readonly hasUnsubmittedChanges = computed(() => this.currentSelectionKey() !== this.submittedSelectionKey());
    protected readonly hasSubmitted = signal(false);

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const groupId = Number(params['groupId']);
            const group = this.mockService.getGroups().find((candidate) => candidate.id === groupId);
            this.group.set(group);

            // Restore any previously handed-in selection so reopening the group reflects it (and no
            // "unsubmitted changes" warning shows for an already-submitted selection).
            const submittedIds = this.selectionService.getSubmittedSelection(groupId);
            this.selectedExercises.set((group?.exercises ?? []).filter((exercise) => exercise.id !== undefined && submittedIds.includes(exercise.id)));
            this.submittedSelectionKey.set(this.keyForIds(submittedIds));
            this.hasSubmitted.set(submittedIds.length > 0);
        });
    }

    /** Mock submit: records the current selection as the submitted one (kept in the shared selection store). */
    protected submitSelection(): void {
        this.submittedSelectionKey.set(this.currentSelectionKey());
        this.hasSubmitted.set(true);
        const groupId = this.group()?.id;
        if (groupId !== undefined) {
            this.selectionService.submitSelection(groupId, this.selectedIds());
        }
    }

    /** Selected exercise ids (mock exercises always carry an id). */
    private selectedIds(): number[] {
        return this.selectedExercises()
            .map((exercise) => exercise.id)
            .filter((id): id is number => id !== undefined);
    }

    private keyForIds(ids: number[]): string {
        return ids
            .slice()
            .sort((a, b) => a - b)
            .join(',');
    }

    protected isSelected(exercise: Exercise): boolean {
        return this.selectedExercises().some((candidate) => candidate.id === exercise.id);
    }

    protected atSelectionLimit(): boolean {
        return this.selectedExercises().length >= this.countTowardsScore();
    }

    protected toggle(exercise: Exercise, checked: boolean): void {
        const current = this.selectedExercises();
        if (checked) {
            if (current.length < this.countTowardsScore()) {
                this.selectedExercises.set(current.concat(exercise));
            }
        } else {
            this.selectedExercises.set(current.filter((candidate) => candidate.id !== exercise.id));
        }
    }

    protected exerciseLink(exercise: Exercise): string {
        return `/courses/${this.courseId}/exercises/${exercise.id}`;
    }
}
