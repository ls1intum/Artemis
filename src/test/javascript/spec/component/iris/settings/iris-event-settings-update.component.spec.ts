import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { IrisEventSettingsUpdateComponent } from '../../../../../../main/webapp/app/iris/settings/iris-settings-update/iris-event-settings-update/iris-event-settings-update.component';
import { AccountService } from '../../../../../../main/webapp/app/core/auth/account.service';
import { IrisEventSettings } from '../../../../../../main/webapp/app/entities/iris/settings/iris-event-settings.model';
import { IrisSettingsType } from '../../../../../../main/webapp/app/entities/iris/settings/iris-settings.model';
import { ArtemisTestModule } from '../../../test.module';
import { mockEvents } from './mock-settings';
import { ComponentRef } from '@angular/core';

describe('IrisEventSettingsUpdateComponent', () => {
    let component: IrisEventSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisEventSettingsUpdateComponent>;
    let componentRef: ComponentRef<IrisEventSettingsUpdateComponent>;
    let accountServiceStub: Partial<AccountService>;

    beforeEach(async () => {
        accountServiceStub = {
            isAdmin: jest.fn().mockReturnValue(true),
        };

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, IrisEventSettingsUpdateComponent],
            declarations: [],
            providers: [{ provide: AccountService, useValue: accountServiceStub }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(IrisEventSettingsUpdateComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        const mockEventSettings = mockEvents();
        componentRef.setInput('eventSettings', mockEventSettings[0]);
        componentRef.setInput('proactivityDisabled', false);
        componentRef.setInput('settingsType', IrisSettingsType.COURSE);
        const parentEventSettings = { ...mockEventSettings[0] } as IrisEventSettings;
        componentRef.setInput('parentEventSettings', parentEventSettings);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize isAdmin correctly', () => {
        expect(component.isAdmin()).toBeTruthy();
    });

    it('should compute inheritDisabled correctly', () => {
        let parentEventSettings = mockEvents()[0];
        parentEventSettings.enabled = true;
        componentRef.setInput('parentEventSettings', parentEventSettings);
        fixture.detectChanges();

        expect(component.inheritDisabled()).toBeFalsy();

        parentEventSettings = mockEvents()[0];
        parentEventSettings.enabled = false;
        componentRef.setInput('parentEventSettings', parentEventSettings);
        fixture.detectChanges();

        expect(component.inheritDisabled()).toBeTruthy();
    });

    it('should compute isSettingsSwitchDisabled correctly', () => {
        expect(component.isSettingsSwitchDisabled()).toBeFalsy();
        // If the parent event settings are disabled, the switch should be disabled
        componentRef.setInput('proactivityDisabled', true);
        fixture.detectChanges();
        expect(component.isSettingsSwitchDisabled()).toBeTruthy();

        componentRef.setInput('proactivityDisabled', false);
        fixture.detectChanges();
        expect(component.isSettingsSwitchDisabled()).toBeFalsy();
    });

    it('should update event settings correctly', () => {
        const newEventSettings = { ...mockEvents()[0] } as IrisEventSettings;
        newEventSettings.enabled = false;
        component.eventSettings.set({ ...newEventSettings } as IrisEventSettings);
        fixture.detectChanges();
        component.updateSetting('enabled', true);
        fixture.detectChanges();
        expect(component.eventSettings().enabled).toBeTruthy();
    });

    it('should render the correct button states based on enabled property', () => {
        component.eventSettings.set({ enabled: true } as IrisEventSettings);
        fixture.detectChanges();
        const activeButton = fixture.debugElement.query(By.css('.btn-success.selected'));
        const inactiveButton = fixture.debugElement.query(By.css('.btn-danger.selected'));
        expect(activeButton).toBeTruthy();
        expect(inactiveButton).toBeFalsy();

        component.eventSettings.set({ enabled: false } as IrisEventSettings);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.btn-success.selected'))).toBeFalsy();
        expect(fixture.debugElement.query(By.css('.btn-danger.selected'))).toBeTruthy();
    });

    it('should disable buttons when isSettingsSwitchDisabled is true', () => {
        componentRef.setInput('proactivityDisabled', true); // should disable the switch
        fixture.detectChanges();
        expect(component.isSettingsSwitchDisabled()).toBeTruthy();
        fixture.detectChanges();
        const buttons = fixture.debugElement.queryAll(By.css('.btn'));
        buttons.forEach((button) => {
            expect(button.nativeElement.classList).toContain('disabled');
        });
    });

    it('should call updateSetting when buttons are clicked', () => {
        jest.spyOn(component, 'updateSetting');
        const activeButton = fixture.debugElement.query(By.css('.btn-success'));

        activeButton.triggerEventHandler('click', null);
        fixture.detectChanges();

        expect(component.updateSetting).toHaveBeenCalledWith('enabled', false);

        const inactiveButton = fixture.debugElement.query(By.css('.btn-danger'));
        inactiveButton.triggerEventHandler('click', null);
        fixture.detectChanges();

        expect(component.updateSetting).toHaveBeenCalledWith('enabled', true);
    });
});
