import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
import { LectureDraft, LectureDraftState } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';

@Component({
    selector: 'jhi-lecture-series-draft-edit-modal',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, TranslateDirective],
    templateUrl: './lecture-series-draft-edit-modal.component.html',
    styleUrl: './lecture-series-draft-edit-modal.component.scss',
})
export class LectureSeriesDraftEditModalComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    lectureDraft: LectureDraft | undefined;
    title = signal<string>('');
    isTitleInvalid = computed(() => this.title().trim() === '');
    startDate = signal<Date | undefined>(undefined);
    endDate = signal<Date | undefined>(undefined);
    isEndDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(this.startDate(), this.endDate()));
    show = signal<boolean>(false);
    areInputsInvalid = computed(() => this.isTitleInvalid() || this.isEndDateInvalid());
    headerTitle = computed<string>(() => this.computeHeaderTitle());

    open(draft: LectureDraft) {
        this.lectureDraft = draft;
        this.title.set(draft.dto.title);
        this.startDate.set(this.convertDayjsDateToDate(draft.dto.startDate));
        this.endDate.set(this.convertDayjsDateToDate(draft.dto.endDate));
        this.show.set(true);
    }

    cancel() {
        this.show.set(false);
        this.clearInputFields();
    }

    save() {
        this.show.set(false);
        const draft = this.lectureDraft;
        if (draft) {
            const dto = draft.dto;
            dto.title = this.title().trim();
            dto.startDate = this.convertDateToDayjsDate(this.startDate());
            dto.endDate = this.convertDateToDayjsDate(this.endDate());
            draft.state = LectureDraftState.EDITED;
        }
        this.clearInputFields();
    }

    private clearInputFields() {
        this.lectureDraft = undefined;
        this.title.set('');
        this.startDate.set(undefined);
        this.endDate.set(undefined);
    }

    private convertDayjsDateToDate(dayjsDate?: Dayjs): Date | undefined {
        return dayjsDate ? dayjsDate.toDate() : undefined;
    }

    private convertDateToDayjsDate(date?: Date): Dayjs | undefined {
        return date ? dayjs(date) : undefined;
    }

    private computeHeaderTitle(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.lecture.createSeries.editLectureModalTitle');
    }
}
