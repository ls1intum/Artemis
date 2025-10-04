import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SelectModule } from 'primeng/select';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { PlannedExerciseSeriesCreateComponent } from 'app/core/course/manage/planned-exercise-modal/planned-exercise-series-create/planned-exercise-series-create.component';
import { PlannedExerciseCreateOrUpdateComponent } from 'app/core/course/manage/planned-exercise-modal/planned-exercise-create/planned-exercise-create-or-update.component';
import { PlannedExerciseComponent } from 'app/core/course/manage/planned-exercise-modal/planned-exercise/planned-exercise.component';
import { PlannedExercise, PlannedExerciseService } from 'app/core/course/shared/services/planned-exercise.service';
//import { addOneMinuteTo, isFirstAfterOrEqualSecond } from 'app/lecture/manage/util/lecture-management.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';

interface CreatePlannedExerciseOption {
    label: string;
    mode: PlannedExerciseCreationMode;
}

enum PlannedExerciseCreationMode {
    SINGLE = 'SINGLE',
    SERIES = 'SERIES',
}

@Component({
    selector: 'jhi-planned-exercise-modal',
    imports: [
        FormsModule,
        DialogModule,
        InputTextModule,
        DatePickerModule,
        ButtonModule,
        SelectButtonModule,
        SelectModule,
        AutoFocusModule,
        FloatLabelModule,
        PlannedExerciseSeriesCreateComponent,
        PlannedExerciseCreateOrUpdateComponent,
        PlannedExerciseComponent,
        FaIconComponent,
    ],
    templateUrl: './planned-exercise-modal.component.html',
    styleUrl: './planned-exercise-modal.component.scss',
})
export class PlannedExerciseModalComponent implements OnInit {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private plannedExerciseService = inject(PlannedExerciseService);

    protected readonly PlannedExerciseCreationMode = PlannedExerciseCreationMode;
    protected readonly faSquarePlus = faPlus;
    protected readonly faXmark = faXmark;

    plannedExercises = this.plannedExerciseService.plannedExercises;
    plannedExerciseToEdit = signal<PlannedExercise | undefined>(undefined);
    createPlannedExerciseOrSeries = signal(false);
    show = signal<boolean>(false);
    headerTitle = computed<string>(() => {
        this.currentLocale();
        return this.translateService.instant('artemisApp.course.exercise.planExercisesButtonLabel');
    });
    createPlannedExerciseOptions: CreatePlannedExerciseOption[] = [
        { label: 'Single Lecture', mode: PlannedExerciseCreationMode.SINGLE },
        { label: 'Lecture Series', mode: PlannedExerciseCreationMode.SERIES },
    ];
    selectedPlannedExerciseCreationMode = signal<PlannedExerciseCreationMode>(PlannedExerciseCreationMode.SINGLE);
    onSelectedPlannedExerciseCreationModeChange(optionMode: PlannedExerciseCreationMode) {
        this.selectedPlannedExerciseCreationMode.set(optionMode);
    }

    ngOnInit() {
        this.plannedExerciseService.load();
    }

    open() {
        this.show.set(true);
    }

    cancel() {
        this.show.set(false);
    }

    add() {
        this.createPlannedExerciseOrSeries.set(true);
    }
}

/*
lectureDraft: LectureDraft | undefined;
    title = signal<string>('');
    isTitleInvalid = computed(() => this.title() === '');
    visibleDate = signal<Date | undefined>(undefined);
    startDate = signal<Date | undefined>(undefined);
    minimumStartDate = computed(() => addOneMinuteTo(this.visibleDate()));
    isStartDateInvalid = computed(() => isFirstAfterOrEqualSecond(this.visibleDate(), this.startDate()));
    endDate = signal<Date | undefined>(undefined);
    minimumEndDate = computed(() => addOneMinuteTo(this.startDate()) ?? addOneMinuteTo(this.visibleDate()));
    isEndDateInvalid = computed(() => isFirstAfterOrEqualSecond(this.visibleDate(), this.endDate()) || isFirstAfterOrEqualSecond(this.startDate(), this.endDate()));
    areInputsInvalid = computed(() => this.isTitleInvalid() || this.isStartDateInvalid() || this.isEndDateInvalid());

save() {
        this.show.set(false);
        const draft = this.lectureDraft;
        if (draft) {
            const dto = draft.dto;
            dto.title = this.title();
            dto.visibleDate = this.convertDateToDayjsDate(this.visibleDate());
            dto.startDate = this.convertDateToDayjsDate(this.startDate());
            dto.endDate = this.convertDateToDayjsDate(this.endDate());
            draft.state = LectureDraftState.EDITED;
        }
        this.clearDraftRelatedFields();
    }

    onTitleChange(value: string) {
        this.title.set(value);
    }

    onVisibleDateChange(value: Date | null) {
        this.visibleDate.set(value ? value : undefined);
    }

    onStartDateChange(value: Date | null) {
        this.startDate.set(value ? value : undefined);
    }

    onEndDateChange(value: Date | null) {
        this.endDate.set(value ? value : undefined);
    }

    private clearDraftRelatedFields() {
        this.lectureDraft = undefined;
        this.title.set('');
        this.visibleDate.set(undefined);
        this.startDate.set(undefined);
        this.endDate.set(undefined);
    }

    private convertDayjsDateToDate(dayjsDate?: Dayjs): Date | undefined {
        return dayjsDate ? dayjsDate.toDate() : undefined;
    }

    private convertDateToDayjsDate(date?: Date): Dayjs | undefined {
        return date ? dayjs(date) : undefined;
    }
 */
