import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { IrisCompetencyGenerationSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisCompetencyGenerationSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-competency-generation-sub-settings-update/iris-competency-generation-sub-settings-update.component';
import { expectElementToBeDisabled, expectElementToBeEnabled } from '../../../helpers/utils/general.utils';

function baseSettings() {
    const mockTemplate = new IrisTemplate();
    mockTemplate.id = 1;
    mockTemplate.content = 'Hello World';
    const irisSubSettings = new IrisCompetencyGenerationSubSettings();
    irisSubSettings.id = 2;
    irisSubSettings.template = mockTemplate;
    irisSubSettings.enabled = true;
    return irisSubSettings;
}

/**
 * gets the full id of an element (as they have the settings type as suffix)
 * @param baseId
 */
function getId(baseId: string) {
    return baseId + IrisSubSettingsType.COMPETENCY_GENERATION;
}

describe('IrisCompetencyGenerationSubSettingsUpdateComponent', () => {
    let component: IrisCompetencyGenerationSubSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisCompetencyGenerationSubSettingsUpdateComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockDirective(NgbTooltip)],
        }).compileComponents();
        fixture = TestBed.createComponent(IrisCompetencyGenerationSubSettingsUpdateComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should show inherit template switch', () => {
        component.subSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector(getId('#inheritTemplate'))).toBeNull();
        expect(fixture.debugElement.nativeElement.querySelector(getId('#template-editor'))).not.toBeNull();

        component.parentSubSettings = baseSettings();
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector(getId('#inheritTemplate'))).not.toBeNull();
        expect(fixture.debugElement.nativeElement.querySelector(getId('#template-editor'))).not.toBeNull();
    });

    it('should disabled template editing when it is inherited', () => {
        component.subSettings = baseSettings();
        component.parentSubSettings = baseSettings();
        const initialTemplateContent = component.subSettings.template?.content;
        fixture.detectChanges();

        //switch inherit template on
        fixture.debugElement.nativeElement.querySelector(getId('#inheritTemplate')).click();
        fixture.detectChanges();

        expectElementToBeDisabled(fixture.debugElement.nativeElement.querySelector(getId('#template-editor')));
        expect(component.subSettings.template).toBeUndefined();

        fixture.debugElement.nativeElement.querySelector(getId('#inheritTemplate')).click();
        fixture.detectChanges();

        expectElementToBeEnabled(fixture.debugElement.nativeElement.querySelector(getId('#template-editor')));
        expect(component.subSettings.template?.content).toEqual(initialTemplateContent);
    });

    it('should register template changes', () => {
        component.subSettings = baseSettings();
        fixture.detectChanges();
        component.templateContent = 'Hello World';
        component.onTemplateChanged();

        expect(component.subSettings.template?.content).toBe('Hello World');
    });

    it('should create template', () => {
        component.subSettings = baseSettings();
        component.subSettings.template = undefined;
        fixture.detectChanges();
        component.templateContent = 'Hello World';
        component.onTemplateChanged();

        expect(component.subSettings.template!.content).toBe('Hello World');
    });

    it('should register sub setting changes', () => {
        component.subSettings = baseSettings();
        fixture.detectChanges();
        const newSubSettings = baseSettings();
        newSubSettings.template!.content = 'Hello World 2';

        const changes: SimpleChanges = {
            subSettings: new SimpleChange(component.subSettings, newSubSettings, false),
        };
        component.subSettings = newSubSettings;
        component.ngOnChanges(changes);

        expect(component.templateContent).toBe('Hello World 2');
    });
});
