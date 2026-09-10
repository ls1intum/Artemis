import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TumUiButtonDirective, TumUiCheckboxComponent, TumUiDialogComponent, TumUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { getCurrentLocaleSignal, onError } from 'app/foundation/util/global.utils';
import { PublicHoliday, TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';

/** One holiday as the dialog lists it, with its selection state. */
interface ImportCandidate {
    readonly holiday: PublicHoliday;
    readonly label: string;
}

/**
 * Offers the public holidays of a span for import as free periods.
 *
 * Where the holidays come from is not settled yet, so the dialog talks to a provider seam on the server rather than to
 * any particular source. While no provider is configured it says so plainly instead of showing an empty list, which
 * would read as "this span has no holidays".
 */
@Component({
    selector: 'jhi-holiday-import-dialog',
    templateUrl: './holiday-import-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, TumUiButtonDirective, TumUiCheckboxComponent, TumUiDialogComponent, TumUiMessageComponent],
})
export class HolidayImportDialogComponent {
    readonly visible = model(false);
    readonly courseId = input.required<number>();
    /** The span offered for import; the page passes the course's tutorial period. */
    readonly from = input.required<dayjs.Dayjs>();
    readonly to = input.required<dayjs.Dayjs>();

    /** The holidays the reader chose, for the page to create. */
    readonly importRequested = output<readonly PublicHoliday[]>();

    private readonly freePeriodService = inject(TutorialGroupFreePeriodService);
    private readonly translateService = inject(TranslateService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly isLoading = signal(false);
    protected readonly providerConfigured = signal(true);
    protected readonly holidays = signal<PublicHoliday[]>([]);
    protected readonly selectedDates = signal<ReadonlySet<string>>(new Set());

    protected readonly candidates = computed<ImportCandidate[]>(() => {
        const locale = this.locale();
        return this.holidays().map((holiday) => ({
            holiday,
            label: dayjs(holiday.date).locale(locale).format('dd, DD MMM YYYY'),
        }));
    });

    protected readonly selectedCount = computed(() => this.selectedDates().size);

    constructor() {
        effect(() => {
            if (this.visible()) {
                this.load();
            }
        });
    }

    private load(): void {
        this.isLoading.set(true);
        this.freePeriodService
            .getPublicHolidays(this.courseId(), this.from(), this.to())
            .pipe(
                finalize(() => this.isLoading.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (suggestions) => {
                    this.providerConfigured.set(suggestions.configured);
                    const holidays = suggestions.holidays ?? [];
                    this.holidays.set(holidays);
                    // Holidays the course already covers start unticked, so importing does not offer to duplicate them.
                    this.selectedDates.set(new Set(holidays.filter((holiday) => !holiday.alreadyExists).map((holiday) => holiday.date)));
                },
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    protected isSelected(holiday: PublicHoliday): boolean {
        return this.selectedDates().has(holiday.date);
    }

    protected toggle(holiday: PublicHoliday, selected: boolean): void {
        const next = new Set(this.selectedDates());
        if (selected) {
            next.add(holiday.date);
        } else {
            next.delete(holiday.date);
        }
        this.selectedDates.set(next);
    }

    protected onImport(): void {
        const selected = this.selectedDates();
        this.importRequested.emit(this.holidays().filter((holiday) => selected.has(holiday.date)));
        this.visible.set(false);
    }

    protected onCancel(): void {
        this.visible.set(false);
    }
}
