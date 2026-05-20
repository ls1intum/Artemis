import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChannelFormComponent, ChannelFormData } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SelectButton } from 'primeng/selectbutton';

describe('ChannelFormComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChannelFormComponent;
    let fixture: ComponentFixture<ChannelFormComponent>;
    const validName = 'group-1';
    const validDescription = 'This is a general channel';
    const validIsPublic = true;
    const validIsAnnouncementChannel = false;
    const validIsCourseWideChannel = false;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                ReactiveFormsModule,
                FormsModule,
                ChannelFormComponent,
                MockComponent(ChannelIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                SelectButton,
            ],
            providers: [
                { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                { provide: DynamicDialogConfig, useValue: { data: {} } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelFormComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should block submit when channel name is missing', () => {
        setFormValid();
        setName(undefined);
        checkFormIsInvalid();

        setFormValid();
        setName('');
        checkFormIsInvalid();
    });

    it('should block submit when channel name pattern is invalid', () => {
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
    });

    it('should not block submit when description is missing', () => {
        setFormValid();
        setDescription(undefined);

        const expectChannelData: ChannelFormData = {
            name: validName,
            description: undefined,
            isPublic: validIsPublic,
            isAnnouncementChannel: validIsAnnouncementChannel,
            isCourseWideChannel: validIsCourseWideChannel,
        };

        clickSubmitButton(true, expectChannelData);
    });

    it('should block submit when description is too long', () => {
        setFormValid();
        setDescription('a'.repeat(256));
        checkFormIsInvalid();
    });

    it('should submit valid form', () => {
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

        clickSubmitButton(true, expectChannelData);
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

    function checkFormIsInvalid() {
        fixture.changeDetectorRef.detectChanges();
        expect(component.form.invalid).toBe(true);
        expect(component.isSubmitPossible).toBe(false);
        clickSubmitButton(false);
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
        fixture.changeDetectorRef.detectChanges();

        if (expectSubmitEvent) {
            expect(submitFormSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledOnce();
            expect(submitFormEventSpy).toHaveBeenCalledWith(expectedFormData);
        } else {
            expect(submitFormSpy).not.toHaveBeenCalled();
            expect(submitFormEventSpy).not.toHaveBeenCalled();
        }
    };
});
