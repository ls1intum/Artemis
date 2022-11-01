import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { MockRouter } from '../../../helpers/mocks/mock-router';

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('TextUnitFormComponent', () => {
    let store = {};

    let textUnitFormComponentFixture: ComponentFixture<TextUnitFormComponent>;
    let textUnitFormComponent: TextUnitFormComponent;
    beforeEach(() => {
        // mocking router
        // mocking the local storage for cache testing
        store = {};
        jest.spyOn(localStorage, 'getItem').mockImplementation((key: string) => {
            return store[key] || null;
        });
        jest.spyOn(localStorage, 'removeItem').mockImplementation((key: string) => {
            delete store[key];
        });
        jest.spyOn(localStorage, 'setItem').mockImplementation((key: string, value: string) => {
            return (store[key] = <string>value);
        });

        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [TextUnitFormComponent, MarkdownEditorStubComponent, MockComponent(FormDateTimePickerComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TranslateService), { provide: Router, useClass: MockRouter }],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                textUnitFormComponentFixture = TestBed.createComponent(TextUnitFormComponent);
                textUnitFormComponent = textUnitFormComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        textUnitFormComponentFixture.detectChanges();
        expect(textUnitFormComponent).not.toBeNull();
    });

    it('should take markdown from cache', fakeAsync(() => {
        // Setting up the fake local storage
        const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
        const fakeUrl = '/test';
        routerMock.setUrl(fakeUrl);
        const cache = {
            markdown: 'Lorem Ipsum',
            date: dayjs().year(2010).month(3).date(5).format('MMM DD YYYY, HH:mm:ss'),
        };
        store[fakeUrl] = JSON.stringify(cache);

        jest.spyOn(window, 'confirm').mockImplementation(() => {
            return true;
        });

        const translateService = TestBed.inject(TranslateService);
        jest.spyOn(translateService, 'instant').mockImplementation(() => {
            return '';
        });
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();

        expect(textUnitFormComponent.content).toEqual(cache.markdown);
    }));

    it('should submit valid form', fakeAsync(() => {
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();

        // simulate setting name
        const exampleName = 'Test';
        textUnitFormComponent.form.get('name')!.setValue(exampleName);
        // simulate setting markdown
        const markdownEditor: MarkdownEditorStubComponent = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorStubComponent)).componentInstance;
        const exampleMarkdown = 'Lorem Ipsum';
        markdownEditor.markdownChange.emit(exampleMarkdown);

        textUnitFormComponentFixture.detectChanges();
        tick(500);
        expect(textUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(textUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(textUnitFormComponent.formSubmitted, 'emit');

        const submitButton = textUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        tick();

        textUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledWith({
                name: 'Test',
                releaseDate: null,
                content: exampleMarkdown,
            });
        });
    }));

    it('should be invalid if name is not set and valid if set', fakeAsync(() => {
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();
        expect(textUnitFormComponent.form.invalid).toBeTrue();
        const name = textUnitFormComponent.form.get('name');
        name!.setValue('');
        expect(textUnitFormComponent.form.invalid).toBeTrue();
        name!.setValue('test');
        expect(textUnitFormComponent.form.invalid).toBeFalse();
    }));

    it('should update local storage on markdown change', fakeAsync(() => {
        const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
        const fakeUrl = '/test';
        routerMock.setUrl(fakeUrl);
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();
        const markdownEditor: MarkdownEditorStubComponent = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorStubComponent)).componentInstance;
        const exampleMarkdown = 'Lorem Ipsum';
        markdownEditor.markdownChange.emit(''); // will be ignored
        tick(500);
        markdownEditor.markdownChange.emit(exampleMarkdown);
        tick(500);
        const savedCache = JSON.parse(store[fakeUrl]);
        expect(savedCache).not.toBeNull();
        expect(savedCache.markdown).toEqual(exampleMarkdown);
        expect(textUnitFormComponent.content).toEqual(exampleMarkdown);
    }));

    it('should correctly set form values in edit mode', fakeAsync(() => {
        const formData: TextUnitFormData = {
            name: 'test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        // init
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        textUnitFormComponent.isEditMode = true;
        tick();

        // setting the form data
        textUnitFormComponent.formData = formData;
        textUnitFormComponent.ngOnChanges(); // ngOnChanges
        textUnitFormComponentFixture.detectChanges();

        expect(textUnitFormComponent.nameControl!.value).toEqual(formData.name);
        expect(textUnitFormComponent.releaseDateControl!.value).toEqual(formData.releaseDate);
        expect(textUnitFormComponent.content).toEqual(formData.content);
        const markdownEditor: MarkdownEditorStubComponent = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorStubComponent)).componentInstance;
        expect(markdownEditor.markdown).toEqual(formData.content);
    }));
});
