import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TutorialGroupSessionFormComponent,
    TutorialGroupSessionFormData,
} from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { NgbTimepickerModule } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from '../../../helpers/tutorialGroupFormsUtils';

describe('TutorialGroupSessionForm', () => {
    let fixture: ComponentFixture<TutorialGroupSessionFormComponent>;
    let component: TutorialGroupSessionFormComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    const validDate = new Date(Date.UTC(2021, 1, 1));
    const validStartTime = '12:00:00';
    const validEndTime = '13:00:00';
    const validLocation = 'Garching';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTimepickerModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            declarations: [TutorialGroupSessionFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionFormComponent);
                component = fixture.componentInstance;
                component.course = course;
                fixture.detectChanges();

                clickSubmit = generateClickSubmitButton(component, fixture, {
                    date: validDate,
                    startTime: validStartTime,
                    endTime: validEndTime,
                    location: validLocation,
                });

                testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        component.isEditMode = true;
        const formData: TutorialGroupSessionFormData = {
            date: validDate,
            startTime: validStartTime,
            endTime: validEndTime,
        };
        fixture.detectChanges();

        component.formData = formData;
        component.ngOnChanges();

        const formControlNames = ['date', 'startTime', 'endTime'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)!.value).toEqual(formData[control]);
        });
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        clickSubmit(true);
    }));

    it('should block submit when time range is invalid', fakeAsync(() => {
        setValidFormValues();

        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        component.endTimeControl!.setValue('11:00:00');
        component.startTimeControl!.setValue('12:00:00');
        fixture.detectChanges();
        expect(component.form.invalid).toBeTrue();
        expect(component.isSubmitPossible).toBeFalse();

        clickSubmit(false);
    }));

    it('should block submit when required property is missing', fakeAsync(() => {
        const requiredControlNames = ['startTime', 'endTime', 'date', 'location'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===

    const setValidFormValues = () => {
        component.dateControl!.setValue(validDate);
        component.startTimeControl!.setValue(validStartTime);
        component.endTimeControl!.setValue(validEndTime);
        component.locationControl!.setValue(validLocation);
    };
});
