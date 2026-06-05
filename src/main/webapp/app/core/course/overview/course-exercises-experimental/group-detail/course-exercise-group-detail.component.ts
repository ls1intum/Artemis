import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faComments, faLayerGroup, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Checkbox } from 'primeng/checkbox';
import { Tag } from 'primeng/tag';
import { DifficultyLevel, Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardElement } from 'app/shared/types/sidebar';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared/components/resizable-panels/resizable-panels.component';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { StudentExerciseDevSettingsService } from '../dev-settings/student-exercise-dev-settings.service';

type TagSeverity = 'success' | 'warn' | 'danger' | 'secondary';

/**
 * Detail page for a course-level exercise group, shown in the experimental student view's right pane
 * when a clickable group is opened. Explains how exercise groups work and lets the student pick which
 * of the group's exercises should count towards their score (read-only group facts otherwise).
 * Dev-only mock — nothing here is persisted.
 */
@Component({
    selector: 'jhi-course-exercise-group-detail',
    templateUrl: './course-exercise-group-detail.component.html',
    styleUrls: ['./course-exercise-group-detail.component.scss'],
    imports: [
        FormsModule,
        RouterLink,
        FaIconComponent,
        Checkbox,
        Tag,
        SidebarCardMediumComponent,
        ResizablePanelsComponent,
        PanelDirective,
        DiscussionSectionComponent,
        CompetencyContributionComponent,
        ArtemisDatePipe,
    ],
})
export class CourseExerciseGroupDetailComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly courseOverviewService = inject(CourseOverviewService);
    private readonly devSettings = inject(StudentExerciseDevSettingsService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faComments = faComments;
    protected readonly faCheck = faCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly getIcon = getIcon;

    protected readonly group = signal<CourseExerciseGroup | undefined>(undefined);
    protected readonly exercises = computed<Exercise[]>(() => this.group()?.exercises ?? []);
    protected readonly firstExercise = computed<Exercise | undefined>(() => this.exercises()[0]);
    // The discussion component derives its course/channel from the exercise; attach the course so the mock
    // channel + messages load (the group itself has no exercise, so we use the first variant as the anchor).
    protected readonly discussionExercise = computed<Exercise | undefined>(() => {
        const exercise = this.firstExercise();
        return exercise ? (Object.assign({}, exercise, { course: { id: this.courseId } as Course }) as Exercise) : undefined;
    });

    // The 'tiles' click action renders the selectable exercises as the real sidebar cards.
    protected readonly useTiles = computed(() => this.devSettings.groupClickAction() === 'tiles');
    // The 'exercise page' click action mimics the exercise detail layout (header + resizable split with communication).
    protected readonly useExercisePage = computed(() => this.devSettings.groupClickAction() === 'exercise-page');
    private readonly cardByExerciseId = computed(() => {
        const map = new Map<number, SidebarCardElement>();
        for (const exercise of this.exercises()) {
            if (exercise.id !== undefined) {
                map.set(exercise.id, this.courseOverviewService.mapExerciseToSidebarCardElement(exercise));
            }
        }
        return map;
    });

    // How many of the group's exercises count towards the score (read-only); caps the selection.
    // TODO temporary test override: groups with more than 2 variants allow selecting 2.
    protected readonly countTowardsScore = computed(() => {
        const group = this.group();
        if ((group?.exercises?.length ?? 0) > 2) {
            return 2;
        }
        return group?.handInLimit ?? 1;
    });

    // The only thing the student can change: which exercises they want assessed.
    protected readonly selectedExercises = signal<Exercise[]>([]);

    // Track which selection has been submitted, so the submit button only enables on unsubmitted changes.
    private readonly submittedSelectionKey = signal('');
    private readonly currentSelectionKey = computed(() =>
        this.selectedExercises()
            .map((exercise) => exercise.id)
            .sort((a, b) => (a ?? 0) - (b ?? 0))
            .join(','),
    );
    protected readonly hasUnsubmittedChanges = computed(() => this.currentSelectionKey() !== this.submittedSelectionKey());
    protected readonly hasSubmitted = signal(false);

    protected courseId = 0;

    constructor() {
        this.courseId = Number(this.route.parent?.parent?.snapshot.params['courseId']);
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const groupId = Number(params['groupId']);
            this.group.set(this.mockService.getGroups().find((candidate) => candidate.id === groupId));
            this.selectedExercises.set([]);
            this.submittedSelectionKey.set('');
            this.hasSubmitted.set(false);
        });
    }

    /** Mock submit: records the current selection as the submitted one (nothing is persisted). */
    protected submitSelection(): void {
        this.submittedSelectionKey.set(this.currentSelectionKey());
        this.hasSubmitted.set(true);
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
        return `/courses/${this.courseId}/exercises/experimental/${exercise.id}`;
    }

    protected cardFor(exercise: Exercise): SidebarCardElement | undefined {
        return exercise.id !== undefined ? this.cardByExerciseId().get(exercise.id) : undefined;
    }

    protected difficultySeverity(difficulty?: DifficultyLevel): TagSeverity {
        switch (difficulty) {
            case DifficultyLevel.EASY:
                return 'success';
            case DifficultyLevel.MEDIUM:
                return 'warn';
            case DifficultyLevel.HARD:
                return 'danger';
            default:
                return 'secondary';
        }
    }

    protected difficultyLabel(difficulty?: DifficultyLevel): string {
        return difficulty ? difficulty.charAt(0) + difficulty.slice(1).toLowerCase() : '';
    }
}
