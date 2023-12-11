import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisHestiaSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisHestiaSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-hestia-sub-settings-update/iris-hestia-sub-settings-update.component';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSubSettings = new IrisHestiaSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.template = mockTemplate;
    irisSubSettings.enabled = true;
    return irisSubSettings;
}

describe('IrisHestiaSubSettingsUpdateComponent Component', () => {
    let comp: IrisHestiaSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisHestiaSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip)],
            declarations: [IrisHestiaSubSettingsUpdateComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisHestiaSubSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('template is not optional', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeFalsy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });

    it('template is optional', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });

    it('template is optional and changes from defined to undefined', () => {
        comp.subSettings = baseSettings();
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        fixture.detectChanges();
        expect(comp.subSettings.template).toBeUndefined();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });

    it('template is optional and changes from undefined to defined', () => {
        const subSettings = baseSettings();
        subSettings.template = undefined;
        comp.subSettings = subSettings;
        comp.parentSubSettings = baseSettings();
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        fixture.detectChanges();
        expect(comp.subSettings.template).toBeDefined();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });

    it('template changes', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        comp.templateContent = 'Hello World 2';
        comp.onTemplateChanged();

        expect(comp.subSettings.template?.content).toBe('Hello World 2');
    });

    it('template created', () => {
        comp.subSettings = baseSettings();
        comp.subSettings.template = undefined;
        fixture.detectChanges();
        comp.templateContent = 'Hello World 2';
        comp.onTemplateChanged();

        expect(comp.subSettings.template!.content).toBe('Hello World 2');
    });

    it('sub settings changes', () => {
        comp.subSettings = baseSettings();
        fixture.detectChanges();
        const newSubSettings = baseSettings();
        newSubSettings.template!.content = 'Hello World 2';

        const changes: SimpleChanges = {
            subSettings: new SimpleChange(comp.subSettings, newSubSettings, false),
        };
        comp.subSettings = newSubSettings;
        comp.ngOnChanges(changes);

        expect(comp.templateContent).toBe('Hello World 2');
    });
});
