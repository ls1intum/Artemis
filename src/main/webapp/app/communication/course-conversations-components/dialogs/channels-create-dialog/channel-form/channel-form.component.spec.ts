import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ChannelFormComponent, ChannelFormData } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ChannelFormComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ChannelFormComponent;
    let fixture: ComponentFixture<ChannelFormComponent>;
    const validName = 'group-1';
    const validDescription = 'This is a general channel';
    const validIsPublic = true;
    const validIsAnnouncementChannel = false;
    const validIsCourseWideChannel = false;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ChannelFormComponent],
            providers: [MockProvider(NgbActiveModal)],
        })
            .overrideComponent(ChannelFormComponent, {
                remove: { imports: [ChannelIconComponent, ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockComponent(ChannelIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ChannelFormComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should block submit when channel name is missing', async () => {
        setFormValid();
        setName(undefined);
        await checkFormIsInvalid();

        setFormValid();
        setName('');
        await checkFormIsInvalid();
    });

    it('should block submit when channel name pattern is invalid', async () => {
        setFormValid();
        setName('has space');
        await checkFormIsInvalid();

        setFormValid();
        setName('has_underscore');
        await checkFormIsInvalid();

        setFormValid();
        setName('hasUpperCase');
        await checkFormIsInvalid();

        setFormValid();
        setName('long-channel-with-31-characters');
        await checkFormIsInvalid();
    });

    it('should not block submit when description is missing', async () => {
        setFormValid();
        setDescription(undefined);

        const expectChannelData: ChannelFormData = {
            name: validName,
            description: undefined,
            isPublic: validIsPublic,
            isAnnouncementChannel: validIsAnnouncementChannel,
            isCourseWideChannel: validIsCourseWideChannel,
        };

        await clickSubmitButton(true, expectChannelData);
    });

    it('should block submit when description is too long', async () => {
        setFormValid();
        setDescription('a'.repeat(256));
        await checkFormIsInvalid();
    });

    it('should submit valid form', async () => {
        setValidFormValues();
        fixture.changeDetectorRef.detectChanges();
        expect(component.form.valid).toBe(true);
        expect(component.isSubmitPossible).toBe(true);

        const expectChannelData: ChannelFormData = {
            name: validName,
            description: validDescription,
            isPublic: validIsPublic,
            isAnnouncementChannel: validIsAnnouncementChannel,
            isCourseWideChannel: validIsCourseWideChannel,
        };

        await clickSubmitButton(true, expectChannelData);
    });

    it('should emit channel type change event when channel type is changed', () => {
        const channelTypeChangeSpy = vi.spyOn(component.channelTypeChanged, 'emit');
        component.channelTypeChanged.emit('PRIVATE');
        expect(channelTypeChangeSpy).toHaveBeenCalledWith('PRIVATE');
    });

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

    async function checkFormIsInvalid() {
        fixture.changeDetectorRef.detectChanges();
        expect(component.form.invalid).toBe(true);
        expect(component.isSubmitPossible).toBe(false);
        await clickSubmitButton(false);
    }
    function setFormValid() {
        setValidFormValues();
        fixture.changeDetectorRef.detectChanges();
        expect(component.form.valid).toBe(true);
        expect(component.isSubmitPossible).toBe(true);
    }
    const clickSubmitButton = (expectSubmitEvent: boolean, expectedFormData?: ChannelFormData) => {
        const submitFormSpy = vi.spyOn(component, 'submitForm');
        const submitFormEventSpy = vi.spyOn(component.formSubmitted, 'emit');

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
