import dayjs from 'dayjs/esm';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { OnlineResourceDTO } from 'app/lecture/lecture-unit/lecture-unit-management/online-resource-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

describe('OnlineUnitFormComponent', () => {
    let onlineUnitFormComponentFixture: ComponentFixture<OnlineUnitFormComponent>;
    let onlineUnitFormComponent: OnlineUnitFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [OnlineUnitFormComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent), MockComponent(FaIconComponent)],
            providers: [MockProvider(OnlineUnitService)],
        })
            .compileComponents()
            .then(() => {
                onlineUnitFormComponentFixture = TestBed.createComponent(OnlineUnitFormComponent);
                onlineUnitFormComponent = onlineUnitFormComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not submit a form when name is missing', () => {
        jest.spyOn(onlineUnitFormComponent, 'urlValidator').mockReturnValue(null);
        onlineUnitFormComponentFixture.detectChanges();
        const exampleDescription = 'lorem ipsum';
        onlineUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        onlineUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        onlineUnitFormComponent.sourceControl!.setValue('https://www.example.com');
        onlineUnitFormComponentFixture.detectChanges();
        expect(onlineUnitFormComponent.form.invalid).toBeTrue();

        const submitFormSpy = jest.spyOn(onlineUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(onlineUnitFormComponent.formSubmitted, 'emit');

        const submitButton = onlineUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return onlineUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).not.toHaveBeenCalled();
            expect(submitFormEventSpy).not.toHaveBeenCalled();
        });
    });

    it('should not submit a form when source is missing', () => {
        jest.spyOn(onlineUnitFormComponent, 'urlValidator').mockReturnValue(null);
        onlineUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        onlineUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleDescription = 'lorem ipsum';
        onlineUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        onlineUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        onlineUnitFormComponentFixture.detectChanges();
        expect(onlineUnitFormComponent.form.invalid).toBeTrue();

        const submitFormSpy = jest.spyOn(onlineUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(onlineUnitFormComponent.formSubmitted, 'emit');

        const submitButton = onlineUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return onlineUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).not.toHaveBeenCalled();
            expect(submitFormEventSpy).not.toHaveBeenCalled();
        });
    });

    it('should submit valid form', () => {
        jest.spyOn(onlineUnitFormComponent, 'urlValidator').mockReturnValue(null);
        onlineUnitFormComponentFixture.detectChanges();
        const exampleName = 'test';
        onlineUnitFormComponent.nameControl!.setValue(exampleName);
        const exampleDescription = 'lorem ipsum';
        onlineUnitFormComponent.descriptionControl!.setValue(exampleDescription);
        const exampleReleaseDate = dayjs().year(2010).month(3).date(5);
        onlineUnitFormComponent.releaseDateControl!.setValue(exampleReleaseDate);
        onlineUnitFormComponent.sourceControl!.setValue('https://www.example.com');
        onlineUnitFormComponentFixture.detectChanges();
        expect(onlineUnitFormComponent.form.valid).toBeTrue();

        const submitFormSpy = jest.spyOn(onlineUnitFormComponent, 'submitForm');
        const submitFormEventSpy = jest.spyOn(onlineUnitFormComponent.formSubmitted, 'emit');

        const submitButton = onlineUnitFormComponentFixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return onlineUnitFormComponentFixture.whenStable().then(() => {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledWith({
                name: exampleName,
                description: exampleDescription,
                releaseDate: exampleReleaseDate,
                source: 'https://www.example.com',
            });

            submitFormSpy.mockRestore();
            submitFormEventSpy.mockRestore();
        });
    });

    it('should correctly set form values in edit mode', () => {
        onlineUnitFormComponent.isEditMode = true;
        const formData: OnlineUnitFormData = {
            name: 'test',
            description: 'lorem ipsum',
            releaseDate: dayjs().year(2010).month(3).date(5),
            source: 'https://www.example.com',
        };
        onlineUnitFormComponentFixture.detectChanges();

        onlineUnitFormComponent.formData = formData;
        onlineUnitFormComponent.ngOnChanges();

        expect(onlineUnitFormComponent.nameControl?.value).toEqual(formData.name);
        expect(onlineUnitFormComponent.releaseDateControl?.value).toEqual(formData.releaseDate);
        expect(onlineUnitFormComponent.descriptionControl?.value).toEqual(formData.description);
        expect(onlineUnitFormComponent.sourceControl?.value).toEqual(formData.source);
    });

    it('should update when link changes', () => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);
        const resourceDto = new OnlineResourceDTO();
        resourceDto.title = 'Cum sociis natoque penatibus';
        resourceDto.description = 'Pellentesque habitant morbi tristique senectus et netus.';
        const response: HttpResponse<OnlineResourceDTO> = new HttpResponse({
            body: resourceDto,
            status: 200,
        });
        const getOnlineResourceStub = jest.spyOn(onlineUnitService, 'getOnlineResource').mockReturnValue(of(response));

        onlineUnitFormComponentFixture.detectChanges();
        onlineUnitFormComponent.isEditMode = true;
        onlineUnitFormComponent.formData = {
            source: 'example.com',
        };
        onlineUnitFormComponent.ngOnChanges();

        // WHEN
        onlineUnitFormComponent.onLinkChanged();

        // THEN
        expect(getOnlineResourceStub).toHaveBeenCalledOnce();
        expect(onlineUnitFormComponent.sourceControl?.valid).toBeTrue();
        expect(onlineUnitFormComponent.sourceControl?.value).toEqual('https://example.com');
        expect(onlineUnitFormComponent.nameControl?.value).toEqual(resourceDto.title);
        expect(onlineUnitFormComponent.descriptionControl?.value).toEqual(resourceDto.description);
    });
});
