import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';
import { MarkdownCache, TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

type Store = {
    [key: string]: any;
};

class MockLocalStorageService {
    private _store: Store = {};

    retrieve<T>(key: string): T | undefined {
        const value = this._store[key];
        return value as T | undefined;
    }

    store<T>(key: string, value: T) {
        this._store[key] = value;
    }

    remove(key: string) {
        delete this._store[key];
    }

    setStoreValue(key: string, value: any) {
        this._store[key] = value;
    }
}

describe('TextUnitFormComponent', () => {
    setupTestBed({ zoneless: true });

    let textUnitFormComponentFixture: ComponentFixture<TextUnitFormComponent>;
    let textUnitFormComponent: TextUnitFormComponent;
    let mockLocalStorageService: MockLocalStorageService;

    beforeEach(async () => {
        mockLocalStorageService = new MockLocalStorageService();
        global.ResizeObserver = MockResizeObserver as any;

        await TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                MockModule(NgbTooltipModule),
                OwlDateTimeModule,
                OwlNativeDateTimeModule,
                FontAwesomeTestingModule,
                TextUnitFormComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                FormDateTimePickerComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CompetencySelectionComponent),
            ],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useValue: mockLocalStorageService },
            ],
        }).compileComponents();

        textUnitFormComponentFixture = TestBed.createComponent(TextUnitFormComponent);
        textUnitFormComponent = textUnitFormComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        textUnitFormComponentFixture.detectChanges();
        expect(textUnitFormComponent).not.toBeNull();
    });

    it('should take markdown from cache', async () => {
        // Setting up the fake local storage
        const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
        const fakeUrl = '/test';
        routerMock.setUrl(fakeUrl);
        const cache: MarkdownCache = {
            markdown: 'Lorem Ipsum',
            date: dayjs().year(2010).month(3).date(5).format('MMM DD YYYY, HH:mm:ss'),
        };
        mockLocalStorageService.setStoreValue(fakeUrl, cache);

        vi.spyOn(window, 'confirm').mockImplementation(() => {
            return true;
        });

        const translateService = TestBed.inject(TranslateService);
        vi.spyOn(translateService, 'instant').mockImplementation(() => {
            return '';
        });
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        await textUnitFormComponentFixture.whenStable();

        expect(textUnitFormComponent.content).toEqual(cache.markdown);
    });

    it('should submit valid form', async () => {
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        await textUnitFormComponentFixture.whenStable();

        // simulate setting name
        const exampleName = 'Test';
        textUnitFormComponent.form.get('name')!.setValue(exampleName);
        // simulate setting markdown
        const markdownEditor: MarkdownEditorMonacoComponent = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
        const exampleMarkdown = 'Lorem Ipsum';
        markdownEditor.markdownChange.emit(exampleMarkdown);

        textUnitFormComponentFixture.detectChanges();
        await new Promise((resolve) => setTimeout(resolve, 500));
        expect(textUnitFormComponent.form.valid).toBe(true);

        const submitFormSpy = vi.spyOn(textUnitFormComponent, 'submitForm');
        const submitFormEventSpy = vi.spyOn(textUnitFormComponent.formSubmitted, 'emit');

        const submitButton = textUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        await textUnitFormComponentFixture.whenStable();

        expect(submitFormSpy).toHaveBeenCalledTimes(1);
        expect(submitFormEventSpy).toHaveBeenCalledWith({
            name: 'Test',
            releaseDate: null,
            competencyLinks: null,
            content: exampleMarkdown,
        });
    });

    it('should be invalid if name is not set and valid if set', async () => {
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        await textUnitFormComponentFixture.whenStable();
        expect(textUnitFormComponent.form.invalid).toBe(true);
        const name = textUnitFormComponent.form.get('name');
        name!.setValue('');
        expect(textUnitFormComponent.form.invalid).toBe(true);
        name!.setValue('test');
        expect(textUnitFormComponent.form.invalid).toBe(false);
    });

    it('should update local storage on markdown change', async () => {
        const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
        const fakeUrl = '/test';
        routerMock.setUrl(fakeUrl);
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        await textUnitFormComponentFixture.whenStable();

        const exampleMarkdown = 'Lorem Ipsum';
        // Call the component's onMarkdownChange directly to test the debounced storage update
        textUnitFormComponent.onMarkdownChange(''); // first change, sets firstMarkdownChangeHappened = true
        await new Promise((resolve) => setTimeout(resolve, 600));
        textUnitFormComponent.onMarkdownChange(exampleMarkdown); // second change, should save
        await new Promise((resolve) => setTimeout(resolve, 600));

        const savedCache = mockLocalStorageService.retrieve<MarkdownCache>(fakeUrl);
        expect(savedCache).not.toBeNull();
        expect(savedCache!.markdown).toEqual(exampleMarkdown);
    });

    it('should correctly set form values in edit mode', async () => {
        const formData: TextUnitFormData = {
            name: 'test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        // init
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        textUnitFormComponentFixture.componentRef.setInput('isEditMode', true);
        await textUnitFormComponentFixture.whenStable();

        textUnitFormComponentFixture.componentRef.setInput('formData', formData);
        textUnitFormComponent.ngOnChanges();
        textUnitFormComponentFixture.detectChanges();
        await textUnitFormComponentFixture.whenStable();

        expect(textUnitFormComponent.nameControl!.value).toEqual(formData.name);
        expect(textUnitFormComponent.releaseDateControl!.value).toEqual(formData.releaseDate);
        expect(textUnitFormComponent.content).toEqual(formData.content);
        // The markdown editor component receives content via input binding - verify the editor exists
        const markdownEditor = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent));
        expect(markdownEditor).not.toBeNull();
    });
});
