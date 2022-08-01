import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { TutorialGroupFormComponent, TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-group-form/tutorial-group-form.component';

describe('TutorialGroupFormComponent', () => {
    let tutorialGroupFormComponentFixture: ComponentFixture<TutorialGroupFormComponent>;
    let tutorialGroupFormComponent: TutorialGroupFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [TutorialGroupFormComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                tutorialGroupFormComponentFixture = TestBed.createComponent(TutorialGroupFormComponent);
                tutorialGroupFormComponent = tutorialGroupFormComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent).not.toBeNull();
    });

    it('should not submit a form when title is missing', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        tutorialGroupFormComponent.titleControl!.setValue(' ');

        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent.form.invalid).toBeTrue();

        const submitFormSpy = jest.spyOn(tutorialGroupFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(tutorialGroupFormComponent.formSubmitted, 'emit');

        const submitButton = tutorialGroupFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return tutorialGroupFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledTimes(0);
            expect(submitFormEventSpy).toHaveBeenCalledTimes(0);
        });
    });

    it('should submit valid form', () => {
        tutorialGroupFormComponentFixture.detectChanges();
        const exampleTitle = 'test';
        tutorialGroupFormComponent.titleControl!.setValue(exampleTitle);

        tutorialGroupFormComponentFixture.detectChanges();
        expect(tutorialGroupFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(tutorialGroupFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(tutorialGroupFormComponent.formSubmitted, 'emit');

        const submitButton = tutorialGroupFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return tutorialGroupFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalled();
            expect(submitFormEventSpy).toHaveBeenCalledWith({
                title: exampleTitle,
            });

            submitFormSpy.mockRestore();
            submitFormEventSpy.mockRestore();
        });
    });

    it('should correctly set form values in edit mode', () => {
        tutorialGroupFormComponent.isEditMode = true;
        const formData: TutorialGroupFormData = {
            title: 'test',
        };
        tutorialGroupFormComponentFixture.detectChanges();

        tutorialGroupFormComponent.formData = formData;
        tutorialGroupFormComponent.ngOnChanges();

        expect(tutorialGroupFormComponent.titleControl?.value).toEqual(formData.title);
    });
});
