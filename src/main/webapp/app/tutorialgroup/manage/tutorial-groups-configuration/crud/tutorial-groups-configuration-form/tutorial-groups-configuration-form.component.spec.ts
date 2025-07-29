import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    TutorialGroupsConfigurationFormComponent,
    TutorialGroupsConfigurationFormData,
} from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from 'test/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';
import { ArtemisDateRangePipe } from 'app/shared/pipes/artemis-date-range.pipe';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('TutorialGroupsConfigurationFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsConfigurationFormComponent>;
    let component: TutorialGroupsConfigurationFormComponent;

    const validPeriodStart = new Date(Date.UTC(2021, 1, 1));
    const validPeriodEnd = new Date(Date.UTC(2021, 2, 1));
    const validPeriod = [validPeriodStart, validPeriodEnd];
    // eslint-disable-next-line no-unused-vars
    let clickSubmit: (expectSubmitEvent: boolean) => void;
    // eslint-disable-next-line no-unused-vars
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTypeaheadModule, OwlDateTimeModule, OwlNativeDateTimeModule, FaIconComponent],
            declarations: [TutorialGroupsConfigurationFormComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDateRangePipe), MockDirective(TranslateDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsConfigurationFormComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('course', { id: 1, postsEnabled: true } as Course);
                clickSubmit = generateClickSubmitButton(component, fixture, {
                    period: validPeriod,
                    usePublicTutorialGroupChannels: true,
                    useTutorialGroupChannels: true,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        fixture.componentRef.setInput('isEditMode', true);
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        fixture.componentRef.setInput('formData', formData);
        fixture.detectChanges();

        const formControlNames = ['period', 'usePublicTutorialGroupChannels', 'useTutorialGroupChannels'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)?.value).toEqual(formData[control as keyof TutorialGroupsConfigurationFormData]);
        });
    });

    it('should show channel deletion warning when channel option is disabled in edit mode', () => {
        fixture.componentRef.setInput('isEditMode', true);
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        fixture.componentRef.setInput('formData', formData);
        fixture.detectChanges();

        component.form.get('useTutorialGroupChannels')!.setValue(false);
        runOnPushChangeDetection(fixture);
        fixture.detectChanges();
        expect(component.showChannelDeletionWarning).toBeTrue();
        const channelDeletionWarning = fixture.nativeElement.querySelector('#channelDeletionWarning');
        expect(channelDeletionWarning).not.toBeNull();
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        runOnPushChangeDetection(fixture);
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        clickSubmit(true);
    }));

    it('should block submit when required property is missing', fakeAsync(() => {
        const requiredControlNames = ['period'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===
    const setValidFormValues = () => {
        component.form.get('period')!.setValue(validPeriod);
        component.form.get('usePublicTutorialGroupChannels')!.setValue(true);
        component.form.get('useTutorialGroupChannels')!.setValue(true);
    };
});
