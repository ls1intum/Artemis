import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import dayjs from 'dayjs/esm';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseTimelineComponent, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';

@Component({
    selector: 'jhi-exercise-group-edit-modal',
    templateUrl: './exercise-group-edit-modal.component.html',
    imports: [FormsModule, DialogModule, InputTextModule, InputNumberModule, ButtonModule, TooltipModule, FaIconComponent, ExerciseTimelineComponent],
})
export class ExerciseGroupEditModalComponent {
    protected readonly faCircleInfo = faCircleInfo;

    readonly visible = input.required<boolean>();
    readonly visibleChange = output<boolean>();
    readonly group = input.required<CourseExerciseGroup>();
    readonly save = output<CourseExerciseGroup>();
    readonly cancelled = output<void>();

    readonly draftTitle = signal('');
    readonly draftMaxPoints = signal<number | undefined>(undefined);
    readonly draftReleaseDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftStartDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly draftAssessmentDueDate = signal<dayjs.Dayjs | undefined>(undefined);

    readonly timelineItems = computed<TimelineItem[]>(() => [
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: this.draftReleaseDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.startDate', date: this.draftStartDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.dueDate', date: this.draftDueDate },
        { kind: 'optional', labelStringKey: 'artemisApp.exercise.assessmentDueDate', date: this.draftAssessmentDueDate },
    ]);

    readonly isTitleValid = computed(() => this.draftTitle().trim().length > 0);

    constructor() {
        effect(() => {
            if (!this.visible()) return;
            const g = this.group();
            this.draftTitle.set(g.title ?? '');
            this.draftMaxPoints.set(g.maxPoints);
            this.draftReleaseDate.set(g.releaseDate);
            this.draftStartDate.set(g.startDate);
            this.draftDueDate.set(g.dueDate);
            this.draftAssessmentDueDate.set(g.assessmentDueDate);
        });
    }

    onSave(): void {
        const updated: CourseExerciseGroup = {
            ...this.group(),
            title: this.draftTitle().trim(),
            maxPoints: this.draftMaxPoints(),
            releaseDate: this.draftReleaseDate(),
            startDate: this.draftStartDate(),
            dueDate: this.draftDueDate(),
            assessmentDueDate: this.draftAssessmentDueDate(),
        };
        this.save.emit(updated);
        this.visibleChange.emit(false);
    }

    onCancel(): void {
        this.cancelled.emit();
        this.visibleChange.emit(false);
    }
}
