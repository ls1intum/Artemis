import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import {
    TutorialGroupsConfigurationFormComponent,
    TutorialGroupsConfigurationFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';
import { ArtemisDateRangePipe } from 'app/shared/pipes/artemis-date-range.pipe';
import { runOnPushChangeDetection } from '../../../../../helpers/on-push-change-detection.helper';
import { Course } from 'app/entities/course.model';

describe('TutorialGroupsConfigurationFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsConfigurationFormComponent>;
    let component: TutorialGroupsConfigurationFormComponent;

    const validPeriodStart = new Date(Date.UTC(2021, 1, 1));
    const validPeriodEnd = new Date(Date.UTC(2021, 2, 1));
    const validPeriod = [validPeriodStart, validPeriodEnd];

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTypeaheadModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            declarations: [TutorialGroupsConfigurationFormComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDateRangePipe), MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsConfigurationFormComponent);
                component = fixture.componentInstance;
                component.course = { id: 1, postsEnabled: true } as Course;
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
        component.isEditMode = true;
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        component.formData = formData;
        component.ngOnChanges();

        const formControlNames = ['period', 'usePublicTutorialGroupChannels', 'useTutorialGroupChannels'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)!.value).toEqual(formData[control]);
        });
    });

    it('should show channel deletion warning when channel option is disabled in edit mode', () => {
        component.isEditMode = true;
        runOnPushChangeDetection(fixture);
        const formData: TutorialGroupsConfigurationFormData = {
            period: validPeriod,
            usePublicTutorialGroupChannels: true,
            useTutorialGroupChannels: true,
        };
        component.formData = formData;
        component.ngOnChanges();

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
