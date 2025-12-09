import { Component, OnChanges, OnDestroy, OnInit, computed, inject, input, output, viewChild } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

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
        FormDateTimePickerComponent,
        CompetencySelectionComponent,
        MarkdownEditorMonacoComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
    ],
})
export class TextUnitFormComponent implements OnInit, OnChanges, OnDestroy {
    private router = inject(Router);
    private translateService = inject(TranslateService);
    private localStorageService = inject(LocalStorageService);

    protected readonly faTimes = faTimes;

    formData = input<TextUnitFormData>();

    isEditMode = input<boolean>(false);
    formSubmitted = output<TextUnitFormData>();

    hasCancelButton = input<boolean>(false);
    onCancel = output<void>();

    datePickerComponent = viewChild(FormDateTimePickerComponent);

    // not included in reactive form
    content: string | undefined;
    contentLoadedFromCache = false;
    firstMarkdownChangeHappened = false;

    private readonly formBuilder = inject(FormBuilder);

    form: FormGroup = this.formBuilder.group({
        name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
        releaseDate: [undefined as dayjs.Dayjs | undefined],
        competencyLinks: [undefined as CompetencyLectureUnitLink[] | undefined],
    });

    private readonly statusChanges = toSignal(this.form.statusChanges ?? 'INVALID');
    isFormValid = computed(() => this.statusChanges() === 'VALID' && this.datePickerComponent()?.isValid());

    private markdownChanges = new Subject<string>();
    private markdownChangesSubscription: Subscription;

    get nameControl() {
        return this.form.get('name');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData()!);
        }
    }

    ngOnDestroy() {
        this.markdownChangesSubscription.unsubscribe();
    }

    ngOnInit(): void {
        const cache = this.localStorageService.retrieve<MarkdownCache>(this.router.url);
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
        this.form.patchValue(formData);
        if (!this.contentLoadedFromCache) {
            this.content = formData.content;
        }
    }

    submitForm() {
        const textUnitFormData: TextUnitFormData = Object.assign({}, this.form.value);
        textUnitFormData.content = this.content;
        this.localStorageService.remove(this.router.url);
        this.formSubmitted.emit(textUnitFormData);
    }

    onMarkdownChange(markdown: string) {
        this.markdownChanges.next(markdown);
    }

    private writeToLocalStorage(markdown: string) {
        const cache: MarkdownCache = { markdown, date: dayjs().format('MMM DD YYYY, HH:mm:ss') };
        this.localStorageService.store<MarkdownCache>(this.router.url, cache);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
