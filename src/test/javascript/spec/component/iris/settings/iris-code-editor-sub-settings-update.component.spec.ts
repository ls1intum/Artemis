import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisCodeEditorSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisCodeEditorSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-code-editor-sub-settings-update/iris-code-editor-sub-settings-update.component';

function mockTemplate(id: number) {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = id;
    mockTemplate.content = 'Hello World';
    return mockTemplate;
}

function baseSettings() {
    const irisSubSettings = new IrisCodeEditorSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.chatTemplate = mockTemplate(1);
    irisSubSettings.problemStatementGenerationTemplate = mockTemplate(2);
    irisSubSettings.templateRepoGenerationTemplate = mockTemplate(3);
    irisSubSettings.solutionRepoGenerationTemplate = mockTemplate(4);
    irisSubSettings.testRepoGenerationTemplate = mockTemplate(5);
    irisSubSettings.enabled = true;
    return irisSubSettings;
}

describe('IrisCodeEditorSubSettingsUpdateComponent Component', () => {
    let comp: IrisCodeEditorSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCodeEditorSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip)],
            declarations: [IrisCodeEditorSubSettingsUpdateComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisCodeEditorSubSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('template is not optional', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeFalsy();
        expect(fixture.debugElement.nativeElement.querySelector('#chat-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#problem-statement-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-repo-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#solution-repo-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#test-repo-template-editor')).toBeTruthy();
    });

    it('template is optional', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#chat-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#problem-statement-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-repo-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#solution-repo-template-editor')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#test-repo-template-editor')).toBeTruthy();
    });

    it('template is optional and changes from defined to undefined', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        fixture.detectChanges();
        expect(comp.subSettings.chatTemplate).toBeUndefined();
        expect(comp.subSettings.problemStatementGenerationTemplate).toBeUndefined();
        expect(comp.subSettings.templateRepoGenerationTemplate).toBeUndefined();
        expect(comp.subSettings.solutionRepoGenerationTemplate).toBeUndefined();
        expect(comp.subSettings.testRepoGenerationTemplate).toBeUndefined();
    });

    it('template is optional and changes from undefined to defined', () => {
        const subSettings = baseSettings();
        subSettings.chatTemplate = undefined;
        subSettings.problemStatementGenerationTemplate = undefined;
        subSettings.templateRepoGenerationTemplate = undefined;
        subSettings.solutionRepoGenerationTemplate = undefined;
        subSettings.testRepoGenerationTemplate = undefined;
        comp.subSettings = subSettings;
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        fixture.detectChanges();
        expect(comp.subSettings.chatTemplate).toBeDefined();
        expect(comp.subSettings.problemStatementGenerationTemplate).toBeDefined();
        expect(comp.subSettings.templateRepoGenerationTemplate).toBeDefined();
        expect(comp.subSettings.solutionRepoGenerationTemplate).toBeDefined();
        expect(comp.subSettings.testRepoGenerationTemplate).toBeDefined();
    });

    it('template changes', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        comp.chatTemplateContent = 'Hello World 2';
        comp.problemStatementGenerationTemplateContent = 'Hello World 3';
        comp.templateRepoGenerationTemplateContent = 'Hello World 4';
        comp.solutionRepoGenerationTemplateContent = 'Hello World 5';
        comp.testRepoGenerationTemplateContent = 'Hello World 6';
        comp.onTemplateChanged();

        expect(comp.subSettings.chatTemplate?.content).toBe('Hello World 2');
        expect(comp.subSettings.problemStatementGenerationTemplate?.content).toBe('Hello World 3');
        expect(comp.subSettings.templateRepoGenerationTemplate?.content).toBe('Hello World 4');
        expect(comp.subSettings.solutionRepoGenerationTemplate?.content).toBe('Hello World 5');
        expect(comp.subSettings.testRepoGenerationTemplate?.content).toBe('Hello World 6');
    });

    it('template created', () => {
        comp.subSettings = baseSettings();
        comp.subSettings.chatTemplate = undefined;
        fixture.detectChanges();
        comp.chatTemplateContent = 'Hello World 2';
        comp.problemStatementGenerationTemplateContent = 'Hello World 3';
        comp.templateRepoGenerationTemplateContent = 'Hello World 4';
        comp.solutionRepoGenerationTemplateContent = 'Hello World 5';
        comp.testRepoGenerationTemplateContent = 'Hello World 6';
        comp.onTemplateChanged();

        expect(comp.subSettings.chatTemplate!.content).toBe('Hello World 2');
        expect(comp.subSettings.problemStatementGenerationTemplate!.content).toBe('Hello World 3');
        expect(comp.subSettings.templateRepoGenerationTemplate!.content).toBe('Hello World 4');
        expect(comp.subSettings.solutionRepoGenerationTemplate!.content).toBe('Hello World 5');
        expect(comp.subSettings.testRepoGenerationTemplate!.content).toBe('Hello World 6');
    });

    it('sub settings changes', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        const newSubSettings = baseSettings();
        newSubSettings.chatTemplate!.content = 'Hello World 2';
        newSubSettings.problemStatementGenerationTemplate!.content = 'Hello World 3';
        newSubSettings.templateRepoGenerationTemplate!.content = 'Hello World 4';
        newSubSettings.solutionRepoGenerationTemplate!.content = 'Hello World 5';
        newSubSettings.testRepoGenerationTemplate!.content = 'Hello World 6';

        const changes: SimpleChanges = {
            subSettings: new SimpleChange(comp.subSettings, newSubSettings, false),
        };
        comp.subSettings = newSubSettings;
        comp.ngOnChanges(changes);

        expect(comp.chatTemplateContent).toBe('Hello World 2');
        expect(comp.problemStatementGenerationTemplateContent).toBe('Hello World 3');
        expect(comp.templateRepoGenerationTemplateContent).toBe('Hello World 4');
        expect(comp.solutionRepoGenerationTemplateContent).toBe('Hello World 5');
        expect(comp.testRepoGenerationTemplateContent).toBe('Hello World 6');
    });
});
