import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { Component, EventEmitter, Output } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { By } from '@angular/platform-browser';
import { Channel } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-channel-form',
    template: '',
})
class ChannelFormStubComponent {
    @Output() formSubmitted: EventEmitter<ChannelFormData> = new EventEmitter<ChannelFormData>();
    @Output() channelTypeChanged: EventEmitter<ChannelType> = new EventEmitter<ChannelType>();
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
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        initializeModal();
    });

    it('clicking close button in modal header should dismiss the modal', () => {
        initializeModal();
        const closeButton = fixture.debugElement.nativeElement.querySelector('.modal-header button');
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        closeButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
    it('should change channel type when channel type is changed in channel form', () => {
        initializeModal();
        expect(component.isPublicChannel).toBeTrue();
        const channelTypeChangedEvent = 'PRIVATE';
        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        form.channelTypeChanged.emit(channelTypeChangedEvent);
        expect(component.isPublicChannel).toBeFalse();
    });
    it('should close modal with the channel to create when form is submitted', () => {
        initializeModal();
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');

        const form: ChannelFormStubComponent = fixture.debugElement.query(By.directive(ChannelFormStubComponent)).componentInstance;
        const formData: ChannelFormData = {
            name: 'test',
            description: 'helloWorld',
            isPublic: true,
        };
        form.formSubmitted.emit(formData);

        const expectedChannel = new Channel();
        expectedChannel.name = formData.name;
        expectedChannel.description = formData.description;
        expectedChannel.isPublic = formData.isPublic;

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(expectedChannel);
    });

    function initializeModal() {
        // expect console.err to be called
        const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
        component.initialize();
        fixture.detectChanges();
        expect(consoleErrorSpy).toHaveBeenCalled();
        expect(component.isInitialized).toBeFalse();
        consoleErrorSpy.mockRestore();

        // expect console.err not to be called
        component.course = course;
        component.initialize();
        fixture.detectChanges();
        expect(component.isInitialized).toBeTrue();
        expect(consoleErrorSpy).not.toHaveBeenCalled();
    }
});
