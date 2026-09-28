import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';

/**
 * The sticky footer of the lecture editor. It keeps Save in view wherever the user is on the page and says what Save covers: the lecture details
 * (title, description, tutorial flag and period). Its secondary button reads Close while nothing needs saving and Cancel once something does.
 */
@Component({
    selector: 'jhi-lecture-edit-footer',
    templateUrl: './lecture-edit-footer.component.html',
    styleUrl: './lecture-edit-footer.component.scss',
    imports: [FaIconComponent, TumAetUiButtonDirective, TumAetUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        // The host sticks, not an element inside it: a sticky element never leaves its parent. The negative margins and offset
        // reach over the padding of the course page's scroll container, so the footer spans it and sits on its lower edge.
        class: 'sticky -bottom-4 z-10 -mx-4 -mb-4 block',
        'data-testid': 'lecture-edit-footer',
    },
})
export class LectureEditFooterComponent {
    protected readonly faSave = faSave;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;

    /** The lecture details differ from what was last saved. */
    readonly hasChanges = input(false);
    readonly isSaving = input(false);
    /** Translation key of why the details cannot be saved, such as a missing title; undefined while they can. */
    readonly invalidReason = input<string>();
    /** When the details were last saved on this page; shown until the next change. */
    readonly savedAt = input<dayjs.Dayjs>();
    /** Translation keys of the sections that hold unsaved changes. */
    readonly changedSections = input<string[]>([]);
    /** Whether the lecture already exists, so its content can be edited on the page. */
    readonly isEditMode = input(false);

    readonly save = output<void>();
    /** Close while nothing needs saving, Cancel otherwise; the page decides what leaving means. */
    readonly leave = output<void>();

    protected readonly isSaveBlocked = computed(() => !this.hasChanges() || !!this.invalidReason() || this.isSaving());

    protected readonly saveBlockedReason = computed<string | undefined>(() => {
        if (this.isSaving()) {
            return undefined;
        }
        if (!this.hasChanges()) {
            return 'artemisApp.lecture.editFooter.nothingToSave';
        }
        return this.invalidReason();
    });

    protected readonly leaveLabel = computed(() => (this.hasChanges() ? 'entity.action.cancel' : 'entity.action.close'));

    // The save button is aria-disabled rather than disabled, so it stays focusable and can explain itself, which leaves it clickable.
    protected onSave(): void {
        if (!this.isSaveBlocked()) {
            this.save.emit();
        }
    }
}
