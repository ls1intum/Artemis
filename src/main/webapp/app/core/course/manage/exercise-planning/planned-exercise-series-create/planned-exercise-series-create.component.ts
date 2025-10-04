import { Component, computed, inject, input, output, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
import { PlannedExercise, PlannedExerciseCreateDTO } from 'app/core/course/shared/entities/planned-exercise.model';
import dayjs, { Dayjs } from 'dayjs/esm';
import { take } from 'rxjs';
import { getFirstAvailableDatePropertyOf } from 'app/core/course/manage/exercise-planning/planned-exercise.util';
import { PlannedExerciseService } from 'app/core/course/shared/services/planned-exercise.service';

@Component({
    selector: 'jhi-planned-exercise-series-create',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, FloatLabelModule, TranslateDirective],
    templateUrl: './planned-exercise-series-create.component.html',
    styleUrl: './planned-exercise-series-create.component.scss',
})
export class PlannedExerciseSeriesCreateComponent {
    private plannedExerciseService = inject(PlannedExerciseService);

    courseId = input.required<number>();
    releaseDate = signal<Date | undefined>(undefined);
    startDate = signal<Date | undefined>(undefined);
    dueDate = signal<Date | undefined>(undefined);
    assessmentDueDate = signal<Date | undefined>(undefined);
    seriesEndDate = signal<Date | undefined>(undefined);
    plannedExercises = computed(() => this.computePlannedExercises(this.releaseDate(), this.startDate(), this.dueDate(), this.assessmentDueDate(), this.seriesEndDate()));
    onCreateFinished = output<void>();

    onReleaseDateChange(date: Date) {
        this.releaseDate.set(date);
    }

    onStartDateChange(date: Date) {
        this.startDate.set(date);
    }

    onDueDateChange(date: Date) {
        this.dueDate.set(date);
    }

    onAssessmentDueDateChange(date: Date) {
        this.assessmentDueDate.set(date);
    }

    onSeriesEndDateChange(date: Date) {
        this.seriesEndDate.set(date);
    }

    save() {
        this.plannedExerciseService
            .createAll(this.plannedExercises(), this.courseId())
            .pipe(take(1))
            .subscribe(() => {
                this.onCreateFinished.emit();
            });
    }

    cancel() {
        this.onCreateFinished.emit();
    }

    private computePlannedExercises(releaseDate?: Date, startDate?: Date, dueDate?: Date, assessmentDueDate?: Date, seriesEndDate?: Date): PlannedExerciseCreateDTO[] {
        if (!seriesEndDate || (!releaseDate && !startDate && !dueDate && !assessmentDueDate)) {
            return [];
        }
        const dtosWithoutTitles: PlannedExerciseCreateDTO[] = [];
        let currentDTO = new PlannedExerciseCreateDTO(
            '',
            releaseDate ? dayjs(releaseDate) : undefined,
            startDate ? dayjs(startDate) : undefined,
            dueDate ? dayjs(dueDate) : undefined,
            assessmentDueDate ? dayjs(assessmentDueDate) : undefined,
        );
        while (this.allDatePropertiesOnOrBeforeSeriesEndDate(currentDTO, dayjs(seriesEndDate))) {
            dtosWithoutTitles.push(currentDTO);
            currentDTO = this.generateNextDTOFromCurrentDTO(currentDTO);
        }
        return this.assignSequentialTitlesTo(dtosWithoutTitles);
    }

    private allDatePropertiesOnOrBeforeSeriesEndDate(dto: PlannedExerciseCreateDTO, end: Dayjs): boolean {
        const dates = [dto.releaseDate, dto.startDate, dto.dueDate, dto.assessmentDueDate].filter((date): date is Dayjs => !!date);
        if (dates.length === 0) return false;
        return dates.every((date) => date.isSameOrBefore(end, 'day'));
    }

    private generateNextDTOFromCurrentDTO(dto: PlannedExerciseCreateDTO): PlannedExerciseCreateDTO {
        return new PlannedExerciseCreateDTO(
            'hey',
            dto.releaseDate ? dto.releaseDate.add(1, 'week') : undefined,
            dto.startDate ? dto.startDate.add(1, 'week') : undefined,
            dto.dueDate ? dto.dueDate.add(1, 'week') : undefined,
            dto.assessmentDueDate ? dto.assessmentDueDate.add(1, 'week') : undefined,
        );
    }

    private assignSequentialTitlesTo(dtos: PlannedExerciseCreateDTO[]): PlannedExerciseCreateDTO[] {
        const existingPlannedExercises = this.plannedExerciseService.plannedExercises();

        enum SortItemType {
            EXISTING = 'EXISTING',
            NEW = 'NEW',
        }
        type SortItem = { type: SortItemType; exercise: PlannedExercise; date: Dayjs } | { type: SortItemType; exercise: PlannedExerciseCreateDTO; date: Dayjs };

        const sortItems: SortItem[] = [
            ...existingPlannedExercises.map((exercise) => ({
                type: SortItemType.EXISTING,
                exercise: exercise,
                date: getFirstAvailableDatePropertyOf(exercise),
            })),
            ...dtos.map((dto) => ({
                type: SortItemType.NEW,
                exercise: dto,
                date: getFirstAvailableDatePropertyOf(dto),
            })),
        ];
        sortItems.sort((first, second) => first.date.valueOf() - second.date.valueOf());
        sortItems.forEach((item, index) => {
            if (item.type === SortItemType.NEW) {
                item.exercise.title = `Planned Exercise ${index + 1}`;
            }
        });

        return sortItems.filter((item) => item.type === SortItemType.NEW).map((item) => item.exercise);
    }
}
