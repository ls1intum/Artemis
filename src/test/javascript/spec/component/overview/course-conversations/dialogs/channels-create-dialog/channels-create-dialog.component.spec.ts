import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { Component, output } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { By } from '@angular/platform-browser';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { initializeDialog } from '../dialog-test-helpers';

@Component({
    selector: 'jhi-channel-form',
    template: '',
})
class ChannelFormStubComponent {
    formSubmitted = output<ChannelFormData>();
    channelTypeChanged = output<ChannelType>();
    isAnnouncementChannelChanged = output<boolean>();
    isCourseWideChannelChanged = output<boolean>();
}

describe('ChannelsCreateDialogComponent', () => {
    let component: ChannelsCreateDialogComponent;
    let fixture: ComponentFixture<ChannelsCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [ChannelsCreateDialogComponent, MockPipe(ArtemisTranslatePipe), ChannelFormStubComponent],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelsCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        initializeDialog(component, fixture, { course });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize the dialog correctly', () => {
        const initializeSpy = jest.spyOn(component, 'initialize');
        component.initialize();
        expect(initializeSpy).toHaveBeenCalledOnce();
        expect(component.course).toBe(course);
    });

    it('clicking close button in modal header should dismiss the modal', () => {
        const closeButton = fixture.debugElement.nativeElement.querySelector('.modal-header button');
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        closeButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
    it('should change channel type when channel type is changed in channel form', () => {
        expect(component.isPublicChannel).toBeTrue();
        const channelTypeChangedEvent = 'PRIVATE';
        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        form.channelTypeChanged.emit(channelTypeChangedEvent);
        expect(component.isPublicChannel).toBeFalse();
    });

    it('should change channel announcement type when channel announcement type is changed in channel form', () => {
        expect(component.isAnnouncementChannel).toBeFalse();
        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        form.isAnnouncementChannelChanged.emit(true);
        expect(component.isAnnouncementChannel).toBeTrue();
    });

    it('should change channel scope type when channel scope type is changed in channel form', () => {
        expect(component.isCourseWideChannel).toBeFalse();
        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        form.isCourseWideChannelChanged.emit(true);
        expect(component.isCourseWideChannel).toBeTrue();
    });

    it('should close modal with the channel to create when form is submitted', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');

        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        const formData: ChannelFormData = {
            name: 'test',
            description: 'helloWorld',
            isPublic: true,
        };
        form.formSubmitted.emit(formData);

        const expectedChannel = new ChannelDTO();
        expectedChannel.name = formData.name;
        expectedChannel.description = formData.description;
        expectedChannel.isPublic = formData.isPublic!;

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(expectedChannel);
    });

    it('should call createChannel with correct data', () => {
        const createChannelSpy = jest.spyOn(component, 'createChannel');

        const formData: ChannelFormData = {
            name: 'testChannel',
            description: 'Test description',
            isPublic: false,
            isAnnouncementChannel: true,
            isCourseWideChannel: false,
        };

        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        form.formSubmitted.emit(formData);

        expect(createChannelSpy).toHaveBeenCalledOnce();
        expect(createChannelSpy).toHaveBeenCalledWith(formData);
    });

    it('should close modal when createChannel is called', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');

        const formData: ChannelFormData = {
            name: 'testChannel',
            description: 'Test description',
            isPublic: true,
            isAnnouncementChannel: false,
            isCourseWideChannel: true,
        };

        component.createChannel(formData);

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                name: formData.name,
                description: formData.description,
                isPublic: formData.isPublic,
            }),
        );
    });
});
