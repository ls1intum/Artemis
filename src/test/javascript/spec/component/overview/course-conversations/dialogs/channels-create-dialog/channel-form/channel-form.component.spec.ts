import { ComponentFixture, TestBed, fakeAsync, waitForAsync } from '@angular/core/testing';
import { ChannelFormComponent, ChannelFormData } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('ChannelFormComponent', () => {
    let component: ChannelFormComponent;
    let fixture: ComponentFixture<ChannelFormComponent>;
    const validName = 'group-1';
    const validDescription = 'This is a general channel';
    const validIsPublic = true;
    const validIsAnnouncementChannel = false;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [ChannelFormComponent, MockComponent(ChannelIconComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelFormComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should block submit when channel name is missing', fakeAsync(() => {
        setFormValid();
        setName(undefined);
        checkFormIsInvalid();

        setFormValid();
        setName('');
        checkFormIsInvalid();
    }));

    it('should block submit when channel name pattern is invalid', fakeAsync(() => {
        setFormValid();
        setName('has space');
        checkFormIsInvalid();

        setFormValid();
        setName('has_underscore');
        checkFormIsInvalid();

        setFormValid();
        setName('hasUpperCase');
        checkFormIsInvalid();

        setFormValid();
        setName('long-channel-with-31-characters');
        checkFormIsInvalid();
    }));

    it('should not block submit when description is missing', fakeAsync(() => {
        setFormValid();
        setDescription(undefined);

        const expectChannelData: ChannelFormData = {
            name: validName,
            description: undefined,
            isPublic: validIsPublic,
            isAnnouncementChannel: validIsAnnouncementChannel,
        };

        clickSubmitButton(true, expectChannelData);
    }));

    it('should block submit when description is too long', fakeAsync(() => {
        setFormValid();
        setDescription('a'.repeat(256));
        checkFormIsInvalid();
    }));

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        const expectChannelData: ChannelFormData = {
            name: validName,
            description: validDescription,
            isPublic: validIsPublic,
            isAnnouncementChannel: validIsAnnouncementChannel,
        };

        clickSubmitButton(true, expectChannelData);
    }));

    it('should emit channel type change event when channel type is changed', fakeAsync(() => {
        const channelTypeChangeSpy = jest.spyOn(component.channelTypeChanged, 'emit');
        component!.isPublicControl!.setValue(false);
        expect(channelTypeChangeSpy).toHaveBeenCalledWith('PRIVATE');
    }));

    function setDescription(description?: string) {
        component!.descriptionControl!.setValue(description);
    }

    function setName(name?: string) {
        component!.nameControl!.setValue(name);
    }

    const setValidFormValues = () => {
        if (component) {
            component?.descriptionControl?.setValue(validDescription);
            component?.nameControl?.setValue(validName);
            component?.isPublicControl?.setValue(validIsPublic);
        }
    };

    function checkFormIsInvalid() {
        fixture.detectChanges();
        expect(component.form.invalid).toBeTrue();
        expect(component.isSubmitPossible).toBeFalse();
        clickSubmitButton(false);
    }
    function setFormValid() {
        setValidFormValues();
        fixture.detectChanges();
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();
    }
    const clickSubmitButton = (expectSubmitEvent: boolean, expectedFormData?: ChannelFormData) => {
        const submitFormSpy = jest.spyOn(component, 'submitForm');
        const submitFormEventSpy = jest.spyOn(component.formSubmitted, 'emit');

        const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();

        return fixture.whenStable().then(() => {
            if (expectSubmitEvent) {
                expect(submitFormSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledOnce();
                expect(submitFormEventSpy).toHaveBeenCalledWith(expectedFormData);
            } else {
                expect(submitFormSpy).not.toHaveBeenCalled();
                expect(submitFormEventSpy).not.toHaveBeenCalled();
            }
        });
    };
});
