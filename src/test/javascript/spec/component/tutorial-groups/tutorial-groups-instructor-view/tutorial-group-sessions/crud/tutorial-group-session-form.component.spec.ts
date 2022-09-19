import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TutorialGroupSessionFormComponent,
    TutorialGroupSessionFormData,
} from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { NgbTimepickerModule } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('TutorialGroupSessionForm', () => {
    let fixture: ComponentFixture<TutorialGroupSessionFormComponent>;
    let component: TutorialGroupSessionFormComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    const validDate = new Date(Date.UTC(2021, 1, 1));
    const validStartTime = '12:00:00';
    const validEndTime = '13:00:00';
    const validLocation = 'Garching';

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

        expect(component.dateControl?.value).toEqual(formData.date);
        expect(component.startTimeControl?.value).toEqual(formData.startTime);
        expect(component.endTimeControl?.value).toBe(formData.endTime);
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        clickSubmit(true);
    }));

    it('should block submit button when form is invalid', fakeAsync(() => {
        const requiredControlNames = ['startTime', 'endTime', 'date', 'location'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===

    const clickSubmit = (expectSubmitEvent: boolean) => {
        const submitFormSpy = jest.spyOn(component, 'submitForm');
        const submitFormEventSpy = jest.spyOn(component.formSubmitted, 'emit');

        const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return fixture.whenStable().then(() => {
            if (expectSubmitEvent) {
                expect(submitFormSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledWith({
                    date: validDate,
                    startTime: validStartTime,
                    endTime: validEndTime,
                    location: validLocation,
                });
            } else {
                expect(submitFormSpy).not.toHaveBeenCalled();
                expect(submitFormEventSpy).not.toHaveBeenCalled();
            }
        });
    };

    const testFormIsInvalidOnMissingRequiredProperty = (controlName: string) => {
        setValidFormValues();

        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        component.form.get(controlName)!.setValue(undefined);
        fixture.detectChanges();
        expect(component.form.invalid).toBeTrue();
        expect(component.isSubmitPossible).toBeFalse();

        clickSubmit(false);
    };

    const setValidFormValues = () => {
        component.dateControl!.setValue(validDate);
        component.startTimeControl!.setValue(validStartTime);
        component.endTimeControl!.setValue(validEndTime);
        component.locationControl!.setValue(validLocation);
    };
});
