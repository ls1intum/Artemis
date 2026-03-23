import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channels-create-dialog.component';
import { MockProvider } from 'ng-mocks';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelFormComponent, ChannelFormData } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { By } from '@angular/platform-browser';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ChannelsCreateDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ChannelsCreateDialogComponent;
    let fixture: ComponentFixture<ChannelsCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChannelsCreateDialogComponent],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(ChannelsCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.changeDetectorRef.detectChanges();
        initializeDialog(component, fixture, { course });
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize the dialog correctly', () => {
        const initializeSpy = vi.spyOn(component, 'initialize');
        component.initialize();
        expect(initializeSpy).toHaveBeenCalledOnce();
        expect(component.course()).toBe(course);
    });

    it('clicking close button in modal header should dismiss the modal', () => {
        const closeButton = fixture.debugElement.nativeElement.querySelector('.modal-header button');
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        closeButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should change channel type when channel type is changed in channel form', () => {
        expect(component.isPublicChannel).toBe(true);
        const channelTypeChangedEvent = 'PRIVATE';
        const formComponentDebug = fixture.debugElement.query(By.css('jhi-channel-form'));
        expect(formComponentDebug).toBeTruthy();

        const formComponent = formComponentDebug.componentInstance;
        expect(formComponent).toBeTruthy();
        formComponent.channelTypeChanged.emit(channelTypeChangedEvent);
        expect(component.isPublicChannel).toBe(false);
    });

    it('should change channel announcement type when channel announcement type is changed in channel form', () => {
        expect(component.isAnnouncementChannel).toBe(false);
        const formComponentDebug = fixture.debugElement.query(By.css('jhi-channel-form'));
        expect(formComponentDebug).toBeTruthy();

        const formComponent = formComponentDebug.componentInstance;
        formComponent.isAnnouncementChannelChanged.emit(true);
        expect(component.isAnnouncementChannel).toBe(true);
    });

    it('should change channel scope type when channel scope type is changed in channel form', () => {
        expect(component.isCourseWideChannel).toBe(false);
        const form: ChannelFormComponent = fixture.debugElement.query(By.directive(ChannelFormComponent)).componentInstance;
        form.isCourseWideChannelChanged.emit(true);
        expect(component.isCourseWideChannel).toBe(true);
    });

    it('should close modal with the channel to create when form is submitted', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');

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

    it('should call createChannel with correct data', () => {
        const createChannelSpy = vi.spyOn(component, 'createChannel');

        const formData: ChannelFormData = {
            name: 'testChannel',
            description: 'Test description',
            isPublic: false,
            isAnnouncementChannel: true,
            isCourseWideChannel: false,
        };

        const form: ChannelFormComponent = fixture.debugElement.query(By.directive(ChannelFormComponent)).componentInstance;
        form.formSubmitted.emit(formData);

        expect(createChannelSpy).toHaveBeenCalledOnce();
        expect(createChannelSpy).toHaveBeenCalledWith(formData);
    });

    it('should close modal when createChannel is called', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');

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
