import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub } from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import * as moment from 'moment';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';

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
    let getItemStub: SinonStub;
    let removeItemStub: SinonStub;
    let setItemStub: SinonStub;
    beforeEach(() => {
        // mocking router
        // mocking the local storage for cache testing
        store = {};
        getItemStub = sandbox.stub(localStorage, 'getItem').callsFake((key: string) => {
            return store[key] || null;
        });
        removeItemStub = sandbox.stub(localStorage, 'removeItem').callsFake((key: string) => {
            delete store[key];
        });
        setItemStub = sandbox.stub(localStorage, 'setItem').callsFake((key: string, value: string) => {
            return (store[key] = <string>value);
        });

        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, ArtemisTestModule],
            declarations: [TextUnitFormComponent, MarkdownEditorStubComponent, MockComponent(FormDateTimePickerComponent), MockPipe(TranslatePipe)],
            schemas: [],
            providers: [{ provide: TranslateService, useValue: MockTranslateService }],
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
        markdownEditor.markdownChange.emit(exampleMarkdown);
        tick(500);
        const savedCache = JSON.parse(store[fakeUrl]);
        expect(savedCache).to.be.ok;
        expect(savedCache.markdown).to.equal(exampleMarkdown);
    }));

    it('should correctly set form values in edit mode', fakeAsync(() => {
        const formData: TextUnitFormData = {
            name: 'test',
            releaseDate: moment({ years: 2010, months: 3, date: 5 }),
            content: 'Lorem Ipsum',
        };

        textUnitFormComponent.isEditMode = true;

        // init
        textUnitFormComponentFixture.detectChanges(); // ngOnInit
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
