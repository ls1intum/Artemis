import { Component, computed, inject, signal } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiDatePickerComponent, TumAetUiDialogComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { LectureDraft, LectureDraftState } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
import { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { isFirstDateAfterOrEqualSecond } from 'app/foundation/util/date.utils';

@Component({
    selector: 'jhi-lecture-series-draft-edit-modal',
    imports: [TumAetUiDialogComponent, TumAetUiInputDirective, TumAetUiDatePickerComponent, TumAetUiButtonDirective, TranslateDirective],
    templateUrl: './lecture-series-draft-edit-modal.component.html',
    styleUrl: './lecture-series-draft-edit-modal.component.scss',
})
export class LectureSeriesDraftEditModalComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    lectureDraft: LectureDraft | undefined;
    title = signal<string>('');
    isTitleInvalid = computed(() => this.title().trim() === '');
    startDate = signal<Dayjs | undefined>(undefined);
    endDate = signal<Dayjs | undefined>(undefined);
    // A picker keeps its last committed value while its text is not a date, so a save must wait for the text to parse.
    isStartTextValid = signal(true);
    isEndTextValid = signal(true);
    isEndDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(this.startDate(), this.endDate()));
    show = signal<boolean>(false);
    areInputsInvalid = computed(() => this.isTitleInvalid() || this.isEndDateInvalid() || !this.isStartTextValid() || !this.isEndTextValid());
    headerTitle = computed<string>(() => this.computeHeaderTitle());

    open(draft: LectureDraft) {
        this.lectureDraft = draft;
        this.title.set(draft.dto.title);
        this.startDate.set(draft.dto.startDate);
        this.endDate.set(draft.dto.endDate);
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
            dto.startDate = this.startDate();
            dto.endDate = this.endDate();
            draft.state = LectureDraftState.EDITED;
        }
        this.clearInputFields();
    }

    protected onTitleInput(event: Event) {
        this.title.set((event.target as HTMLInputElement).value);
    }

    private clearInputFields() {
        this.lectureDraft = undefined;
        this.title.set('');
        this.startDate.set(undefined);
        this.endDate.set(undefined);
        this.isStartTextValid.set(true);
        this.isEndTextValid.set(true);
    }

    private computeHeaderTitle(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.lecture.createSeries.editLectureModalTitle');
    }
}
