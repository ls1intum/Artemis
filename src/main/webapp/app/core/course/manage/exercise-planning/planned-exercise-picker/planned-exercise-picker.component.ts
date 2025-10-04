import { Component, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { PlannedExercise } from 'app/core/course/shared/entities/planned-exercise.model';
import { PlannedExerciseService } from 'app/core/course/shared/services/planned-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';

interface PlannedExerciseOption {
    label: string;
    plannedExerciseId: number;
}

@Component({
    selector: 'jhi-planned-exercise-picker',
    imports: [FormsModule, SelectModule],
    templateUrl: './planned-exercise-picker.component.html',
    styleUrl: './planned-exercise-picker.component.scss',
})
export class PlannedExercisePickerComponent implements OnInit {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private plannedExerciseService = inject(PlannedExerciseService);
    private plannedExercises = this.plannedExerciseService.plannedExercises;

    courseId = input.required<number>();
    loading = this.plannedExerciseService.loading;
    selectedPlannedExercise = output<PlannedExercise | undefined>();
    plannedExerciseOptions = computed<PlannedExerciseOption[]>(() => this.computePlannedExerciseOptions(this.plannedExercises()));
    selectedPlannedExerciseId = signal<number | undefined>(undefined);
    placeholder = computed(() => {
        this.currentLocale();
        return this.translateService.instant('artemisApp.course.exercise.plannedExercisePickerPlaceholder');
    });

    constructor() {
        effect(() => {
            const selectedPlannedExercise = this.computeSelectedPlannedExercise(this.plannedExercises(), this.selectedPlannedExerciseId());
            this.selectedPlannedExercise.emit(selectedPlannedExercise);
        });
    }

    ngOnInit() {
        this.plannedExerciseService.getAll(this.courseId());
    }

    onSelectedPlannedExerciseChange(plannedExerciseId?: number) {
        this.selectedPlannedExerciseId.set(plannedExerciseId);
    }

    private computePlannedExerciseOptions(plannedExercises: PlannedExercise[]): PlannedExerciseOption[] {
        return plannedExercises.map((exercise) => ({ label: exercise.title, plannedExerciseId: exercise.id }));
    }

    private computeSelectedPlannedExercise(plannedExercises: PlannedExercise[], selectedPlannedExerciseId?: number): PlannedExercise | undefined {
        return selectedPlannedExerciseId ? plannedExercises.find((exercise) => exercise.id === selectedPlannedExerciseId) : undefined;
    }
}
