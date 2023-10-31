import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisChatSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { IrisChatSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-chat-sub-settings-update/iris-chat-sub-settings-update.component';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSubSettings = new IrisChatSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.template = mockTemplate;
    irisSubSettings.enabled = true;
    return irisSubSettings;
}

describe('IrisSubSettingsUpdateComponent Component', () => {
    let comp: IrisChatSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisChatSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip)],
            declarations: [IrisChatSubSettingsUpdateComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisChatSubSettingsUpdateComponent);
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
});
