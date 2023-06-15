import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-sub-settings-update/iris-sub-settings-update.component';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSubSettings = new IrisSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.template = mockTemplate;
    irisSubSettings.enabled = true;
    return irisSubSettings;
}

describe('IrisSubSettingsUpdateComponent Component', () => {
    let comp: IrisSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [IrisSubSettingsUpdateComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisSubSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('template is not optional', () => {
        comp.subSettings = baseSettings();
        comp.templateOptional = false;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeFalsy();
    });

    it('template is optional and defined', () => {
        comp.subSettings = baseSettings();
        comp.templateOptional = true;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });

    it('template is optional and undefined', () => {
        const subSettings = baseSettings();
        subSettings.template = undefined;
        comp.subSettings = subSettings;
        comp.templateOptional = true;
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeFalsy();
    });

    it('template is optional and changes from defined to undefined', () => {
        comp.subSettings = baseSettings();
        comp.templateOptional = true;
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        expect(comp.subSettings.template).toBeUndefined();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeFalsy();
    });

    it('template is optional and changes from undefined to defined', () => {
        const subSettings = baseSettings();
        subSettings.template = undefined;
        comp.subSettings = subSettings;
        comp.templateOptional = true;
        fixture.detectChanges();
        comp.onInheritTemplateChanged();
        expect(comp.subSettings.template).toBeDefined();
        expect(fixture.debugElement.nativeElement.querySelector('#inheritTemplate')).toBeTruthy();
        expect(fixture.debugElement.nativeElement.querySelector('#template-editor')).toBeTruthy();
    });
});
