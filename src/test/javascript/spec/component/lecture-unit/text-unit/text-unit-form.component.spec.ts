import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../../helpers/mocks/mock-router';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('TextUnitFormComponent', () => {
    let store = {};
    const sandbox = sinon.createSandbox();
    let textUnitFormComponentFixture: ComponentFixture<TextUnitFormComponent>;
    let textUnitFormComponent: TextUnitFormComponent;
    beforeEach(() => {
        // mocking router
        // mocking the local storage for cache testing
        store = {};
        sandbox.stub(localStorage, 'getItem').callsFake((key: string) => {
            return store[key] || null;
        });
        sandbox.stub(localStorage, 'removeItem').callsFake((key: string) => {
            delete store[key];
        });
        sandbox.stub(localStorage, 'setItem').callsFake((key: string, value: string) => {
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

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        textUnitFormComponentFixture.detectChanges();
        expect(textUnitFormComponent).to.be.ok;
    });

    it('should take markdown from cache', fakeAsync(() => {
        // Setting up the fake local storage
        const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as any);
        const fakeUrl = '/test';
        routerMock.setUrl(fakeUrl);
        const cache = {
            markdown: 'Lorem Ipsum',
            date: moment({ years: 2010, months: 3, date: 5 }).format('MMM DD YYYY, HH:mm:ss'),
        };
        store[fakeUrl] = JSON.stringify(cache);

        sandbox.stub(window, 'confirm').callsFake(() => {
            return true;
        });

        const translateService = TestBed.inject(TranslateService);
        sandbox.stub(translateService, 'instant').callsFake(() => {
            return '';
        });
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();

        expect(textUnitFormComponent.content).to.equal(cache.markdown);
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
        expect(textUnitFormComponent.form.valid).to.be.true;

        const submitFormSpy = sinon.spy(textUnitFormComponent, 'submitForm');
        const submitFormEventSpy = sinon.spy(textUnitFormComponent.formSubmitted, 'emit');

        const submitButton = textUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        textUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).to.have.been.called;
            expect(submitFormEventSpy).to.have.been.calledWith({
                name: 'Test',
                releaseDate: null,
                content: exampleMarkdown,
            });
        });

        submitFormSpy.restore();
        submitFormEventSpy.restore();
    }));

    it('should be invalid if name is not set and valid if set', fakeAsync(() => {
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
        tick();
        expect(textUnitFormComponent.form.invalid).to.be.true;
        const name = textUnitFormComponent.form.get('name');
        name!.setValue('');
        expect(textUnitFormComponent.form.invalid).to.be.true;
        name!.setValue('test');
        expect(textUnitFormComponent.form.invalid).to.be.false;
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
        expect(savedCache).to.be.ok;
        expect(savedCache.markdown).to.equal(exampleMarkdown);
        expect(textUnitFormComponent.content).to.equal(exampleMarkdown);
    }));

    it('should correctly set form values in edit mode', fakeAsync(() => {
        const formData: TextUnitFormData = {
            name: 'test',
            releaseDate: moment({ years: 2010, months: 3, date: 5 }),
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

        expect(textUnitFormComponent.nameControl!.value).to.equal(formData.name);
        expect(textUnitFormComponent.releaseDateControl!.value).to.equal(formData.releaseDate);
        expect(textUnitFormComponent.content).to.equal(formData.content);
        const markdownEditor: MarkdownEditorStubComponent = textUnitFormComponentFixture.debugElement.query(By.directive(MarkdownEditorStubComponent)).componentInstance;
        expect(markdownEditor.markdown).to.equal(formData.content);
    }));
});
