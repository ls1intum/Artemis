import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';

export interface TextUnitFormData {
    name?: string;
    releaseDate?: dayjs.Dayjs;
    content?: string;
}

@Component({
    selector: 'jhi-text-unit-form',
    templateUrl: './text-unit-form.component.html',
    styles: [],
})
export class TextUnitFormComponent implements OnInit, OnChanges, OnDestroy {
    @Input()
    formData: TextUnitFormData = {
        name: undefined,
        releaseDate: undefined,
        content: undefined,
    };

    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TextUnitFormData> = new EventEmitter<TextUnitFormData>();

    form: FormGroup;
    // not included in reactive form
    content: string | undefined;
    contentLoadedFromCache = false;
    firstMarkdownChangeHappened = false;

    private markdownChanges = new Subject<string>();
    private markdownChangesSubscription: Subscription;

    constructor(private fb: FormBuilder, private router: Router, private translateService: TranslateService) {}

    get nameControl() {
        return this.form.get('name');
    }

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnDestroy() {
        this.markdownChangesSubscription.unsubscribe();
    }

    ngOnInit(): void {
        if (window.localStorage && window.localStorage.getItem(this.router.url)) {
            const cache = JSON.parse(window.localStorage.getItem(this.router.url)!);

            if (confirm(this.translateService.instant('artemisApp.textUnit.cachedMarkdown') + ' ' + cache.date)) {
                this.content = cache.markdown!;
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
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group({
            name: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)]],
            releaseDate: [undefined as dayjs.Dayjs | undefined],
        });
    }

    private setFormValues(formData: TextUnitFormData) {
        this.form.patchValue(formData);
        if (!this.contentLoadedFromCache) {
            this.content = formData.content;
        }
    }

    submitForm() {
        const textUnitFormData: TextUnitFormData = { ...this.form.value };
        textUnitFormData.content = this.content;
        if (window.localStorage) {
            localStorage.removeItem(this.router.url);
        }
        this.formSubmitted.emit(textUnitFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    onMarkdownChange(markdown: string) {
        this.markdownChanges.next(markdown);
    }

    private writeToLocalStorage(markdown: string) {
        if (window.localStorage) {
            const cache = { markdown, date: dayjs().format('MMM DD YYYY, HH:mm:ss') };
            localStorage.setItem(this.router.url, JSON.stringify(cache));
        }
    }
}
