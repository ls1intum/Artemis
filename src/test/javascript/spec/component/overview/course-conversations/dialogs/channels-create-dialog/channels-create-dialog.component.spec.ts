import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { ChannelFormData } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { By } from '@angular/platform-browser';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { initializeDialog } from '../dialog-test-helpers';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ChannelsCreateDialogComponent', () => {
    let component: ChannelsCreateDialogComponent;
    let fixture: ComponentFixture<ChannelsCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [ChannelsCreateDialogComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ChannelsCreateDialogComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
                initializeDialog(component, fixture, { course });
            });
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
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
        const formComponentDebug = fixture.debugElement.query(By.css('jhi-channel-form'));
        expect(formComponentDebug).toBeTruthy();

        const formComponent = formComponentDebug.componentInstance;
        expect(formComponent).toBeTruthy();
        formComponent.channelTypeChanged.emit(channelTypeChangedEvent);
        expect(component.isPublicChannel).toBeFalse();
    });

    it('should change channel announcement type when channel announcement type is changed in channel form', () => {
        expect(component.isAnnouncementChannel).toBeFalse();
        const formComponentDebug = fixture.debugElement.query(By.css('jhi-channel-form'));
        expect(formComponentDebug).toBeTruthy();

        const formComponent = formComponentDebug.componentInstance;
        formComponent.isAnnouncementChannelChanged.emit(true);
        expect(component.isAnnouncementChannel).toBeTrue();
    });

    it('should close modal with the channel to create when form is submitted', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');

        const formComponentDebug = fixture.debugElement.query(By.css('jhi-channel-form'));
        expect(formComponentDebug).toBeTruthy();

        const formComponent = formComponentDebug.componentInstance;
        const formData: ChannelFormData = {
            name: 'test',
            description: 'helloWorld',
            isPublic: true,
        };
        formComponent.formSubmitted.emit(formData);

        const expectedChannel = new ChannelDTO();
        expectedChannel.name = formData.name;
        expectedChannel.description = formData.description;
        expectedChannel.isPublic = formData.isPublic!;

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(expectedChannel);
    });
});
