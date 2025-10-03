import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
//import { LectureDraft, LectureDraftState } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
//import dayjs, { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
//import { addOneMinuteTo, isFirstAfterOrEqualSecond } from 'app/lecture/manage/util/lecture-management.utils';

@Component({
    selector: 'jhi-planned-exercise-modal',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, TranslateDirective],
    templateUrl: './planned-exercise-modal.component.html',
    styleUrl: './planned-exercise-modal.component.scss',
})
export class PlannedExerciseModalComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    show = signal<boolean>(false);

    headerTitle = computed<string>(() => {
        this.currentLocale();
        return this.translateService.instant('artemisApp.lecture.createSeries.editLectureModalTitle');
    });

    open() {
        this.show.set(true);
    }

    cancel() {
        this.show.set(false);
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
