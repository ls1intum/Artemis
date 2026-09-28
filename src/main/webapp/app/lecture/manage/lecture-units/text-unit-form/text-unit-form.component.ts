import { Component, OnDestroy, OnInit, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { UnitFormChange } from 'app/lecture/manage/lecture-units/unit-form-change.model';
import { TumAetUiButtonDirective, TumAetUiDatePickerComponent, TumAetUiFormFieldComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

export interface TextUnitFormData {
    name?: string;
    releaseDate?: dayjs.Dayjs;
    content?: string;
    competencyLinks?: CompetencyLectureUnitLink[];
}

export type MarkdownCache = {
    markdown: string;
    date: string;
};

@Component({
    selector: 'jhi-text-unit-form',
    templateUrl: './text-unit-form.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        TumAetUiButtonDirective,
        TumAetUiDatePickerComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        CompetencySelectionComponent,
        MarkdownEditorMonacoComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
    ],
})
export class TextUnitFormComponent implements OnInit, OnDestroy {
    private router = inject(Router);
    private translateService = inject(TranslateService);
    private localStorageService = inject(LocalStorageService);
    private readonly formBuilder = inject(FormBuilder);

    protected readonly faTimes = faTimes;

    formData = input<TextUnitFormData>();

    isEditMode = input<boolean>(false);
    formSubmitted = output<TextUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    /** Reports every change instead of offering Submit, for an item that is edited in place and saved automatically. */
    readonly autosave = input<boolean>(false);
    readonly formChanged = output<UnitFormChange<TextUnitFormData>>();
    /** Set while the form takes over the data of the item, which is not a change of the user. */
    private applyingFormData = false;

    /** The release date picker keeps its last valid date while the typed text is not a date yet, so that text is tracked separately. */
    readonly isReleaseDateTextValid = signal(true);

    readonly markdownEditor = viewChild(MarkdownEditorMonacoComponent);

    // not included in reactive form; backed by a signal so the [(markdown)] two-way binding re-renders under zoneless
    private readonly _content = signal<string | undefined>(undefined);
    get content(): string | undefined {
        return this._content();
    }
    set content(content: string | undefined) {
        this._content.set(content);
    }
    contentLoadedFromCache = false;
    firstMarkdownChangeHappened = false;

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });

    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    isFormValid = computed(() => this.statusChanges() === 'VALID' && this.isReleaseDateTextValid());

    private markdownChanges = new Subject<string>();
    private markdownChangesSubscription!: Subscription; // set in ngOnInit(), always before ngOnDestroy() unsubscribes

    // Tracks the formData reference already applied to the form so the patching effect stays idempotent.
    private appliedFormData?: TextUnitFormData;

    constructor() {
        // Patch the form with the provided data in edit mode (replaces ngOnChanges).
        // Patch ONCE per distinct formData value: `form.patchValue()` synchronously emits the form's
        // statusChanges, which is mirrored into the `statusChanges` signal via `toSignal(...)`. Under
        // zoneless that signal write reschedules the reactive flush, which re-runs this effect, which
        // patches again — an infinite change-detection loop that pegged the main thread and left the
        // edit form permanently stuck behind the loading spinner. Guarding on the formData reference
        // breaks the cycle and also avoids clobbering in-progress user edits on later flushes.
        effect(() => {
            const data = this.formData();
            if (this.isEditMode() && data && data !== this.appliedFormData) {
                this.appliedFormData = data;
                this.setFormValues(data);
            }
        });

        this.nameControl!.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reportChange(false));
        this.form
            .get('competencyLinks')!
            .valueChanges.pipe(takeUntilDestroyed())
            .subscribe(() => this.reportChange(true));
    }

    get nameControl() {
        return this.form.get('name');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    onReleaseDateChange(releaseDate: dayjs.Dayjs | undefined): void {
        this.releaseDateControl?.setValue(releaseDate);
        this.releaseDateControl?.markAsDirty();
        this.reportChange(true);
    }

    onReleaseDateTextValidityChange(valid: boolean): void {
        // The picker also reports the validity it starts with, which is no change of the user.
        if (valid === this.isReleaseDateTextValid()) {
            return;
        }
        this.isReleaseDateTextValid.set(valid);
        this.reportChange(false);
    }

    private reportChange(immediate: boolean): void {
        if (!this.autosave() || this.applyingFormData) {
            return;
        }
        // A control reports its change before the form takes it over, so the form's own value and validity lag one change behind here.
        const valid = Object.values(this.form.controls).every((control) => !control.invalid) && this.isReleaseDateTextValid();
        this.formChanged.emit({ data: this.currentFormData(), immediate, valid });
    }

    private currentFormData(): TextUnitFormData {
        const textUnitFormData: TextUnitFormData = deepClone(this.form.getRawValue());
        textUnitFormData.content = this.content;
        return textUnitFormData;
    }

    ngOnDestroy() {
        this.markdownChangesSubscription.unsubscribe();
    }

    ngOnInit(): void {
        // An item edited in place is saved automatically, so there is no draft to keep in the browser.
        const cache = this.autosave() ? undefined : this.localStorageService.retrieve<MarkdownCache>(this.router.url);
        if (cache) {
            if (confirm(this.translateService.instant('artemisApp.textUnit.cachedMarkdown') + ' ' + cache.date)) {
                this.content = cache.markdown;
                this.contentLoadedFromCache = true;
            }
        }
        this.markdownChangesSubscription = this.markdownChanges.pipe(debounceTime(500)).subscribe((markdown) => {
            // so we do not overwrite the cache immediately we ignore the initial markdown change
            if (this.firstMarkdownChangeHappened) {
                this.writeToLocalStorage(markdown);
            } else {
                this.firstMarkdownChangeHappened = true;
            }
        });
    }

    private setFormValues(formData: TextUnitFormData) {
        this.applyingFormData = true;
        this.form.patchValue(formData);
        if (!this.contentLoadedFromCache) {
            this.content = formData.content;
        }
        this.applyingFormData = false;
    }

    submitForm() {
        if (this.autosave()) {
            // Enter in a field of an item that saves itself saves at once instead of creating an item.
            this.reportChange(true);
            return;
        }
        this.localStorageService.remove(this.router.url);
        this.formSubmitted.emit(this.currentFormData());
    }

    /**
     * Reports text that the markdown editor did not report yet: it waits a moment after typing, which a save that closes the form, or
     * leaving the form, would cut off.
     */
    flushPendingEdits(): void {
        const text = this.markdownEditor()?.monacoEditor()?.getText();
        if (!this.autosave() || text === undefined || text === (this.content ?? '')) {
            return;
        }
        this.content = text;
        this.reportChange(true);
    }

    onMarkdownChange(markdown: string) {
        this.markdownChanges.next(markdown);
        this.reportChange(false);
    }

    private writeToLocalStorage(markdown: string) {
        if (this.autosave()) {
            return;
        }
        const cache: MarkdownCache = { markdown, date: dayjs().format('MMM DD YYYY, HH:mm:ss') };
        this.localStorageService.store<MarkdownCache>(this.router.url, cache);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
