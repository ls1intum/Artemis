import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackDetailChannelModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-detail-channel-modal.component';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

describe('FeedbackDetailChannelModalComponent', () => {
    let fixture: ComponentFixture<FeedbackDetailChannelModalComponent>;
    let component: FeedbackDetailChannelModalComponent;
    let activeModal: NgbActiveModal;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ReactiveFormsModule, FeedbackDetailChannelModalComponent],
            providers: [NgbActiveModal, NgbModal, FormBuilder],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackDetailChannelModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        modalService = TestBed.inject(NgbModal);

        fixture.componentRef.setInput('feedbackDetail', {
            detailTexts: ['Sample feedback'],
            count: 10,
            relativeCount: 50,
            testCaseName: 'testCase1',
            taskName: 'Task 1',
            errorCategory: 'StudentError',
        } as any);
        fixture.componentInstance.isConfirmModalOpen.set(false);
        fixture.detectChanges();
    });

    it('should initialize form and inputs', () => {
        expect(component.feedbackDetail().detailTexts).toStrictEqual(['Sample feedback']);
        expect(component.form).toBeDefined();
        expect(component.form.valid).toBeFalse();
    });

    it('should call activeModal.close when closeModal is triggered', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.closeModal();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should call activeModal.dismiss when dismissModal is triggered', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        component.dismissModal();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should open confirmation modal and emit formSubmitted on successful confirmation', async () => {
        jest.spyOn(component, 'handleModal').mockResolvedValue(true);

        component.form.setValue({
            name: 'channel',
            description: 'channelDescription',
            isPrivate: true,
            isAnnouncementChannel: false,
        });

        const formSubmittedSpy = jest.spyOn(component.formSubmitted, 'emit');
        await component.submitForm(false);

        expect(component.isConfirmModalOpen()).toBeFalse();
        expect(formSubmittedSpy).toHaveBeenCalledExactlyOnceWith({
            channelDto: expect.objectContaining({
                creationDate: undefined,
                creator: undefined,
                description: 'channelDescription',
                hasChannelModerationRights: false,
                hasUnreadMessage: undefined,
                id: undefined,
                isAnnouncementChannel: false,
                isArchived: false,
                isChannelModerator: false,
                isCourseWide: false,
                isCreator: undefined,
                isFavorite: undefined,
                isHidden: undefined,
                isMember: undefined,
                isMuted: undefined,
                isPublic: false,
                lastMessageDate: undefined,
                lastReadDate: undefined,
                name: 'channel',
                numberOfMembers: undefined,
                subType: undefined,
                subTypeReferenceId: undefined,
                topic: 'FeedbackDiscussion',
                tutorialGroupId: undefined,
                tutorialGroupTitle: undefined,
                type: 'channel',
                unreadMessagesCount: undefined,
            }),
            navigate: false,
        });
    });

    it('should call handleModal and proceed if confirmed', async () => {
        jest.spyOn(component, 'handleModal').mockResolvedValue(true);
        const formSubmittedSpy = jest.spyOn(component.formSubmitted, 'emit');

        component.form.setValue({
            name: 'channel',
            description: 'channelDescription',
            isPrivate: false,
            isAnnouncementChannel: false,
        });

        await component.submitForm(false);

        expect(component.handleModal).toHaveBeenCalledOnce();
        expect(formSubmittedSpy).toHaveBeenCalledExactlyOnceWith({
            channelDto: expect.objectContaining({
                name: 'channel',
                description: 'channelDescription',
                isPublic: true,
                isAnnouncementChannel: false,
            }),
            navigate: false,
        });
    });

    it('should not proceed if modal is dismissed', async () => {
        jest.spyOn(component, 'handleModal').mockResolvedValue(false);

        const formSubmittedSpy = jest.spyOn(component.formSubmitted, 'emit');

        component.form.setValue({
            name: 'channel',
            description: 'channelDescription',
            isPrivate: true,
            isAnnouncementChannel: false,
        });

        await component.submitForm(false);

        expect(component.handleModal).toHaveBeenCalledOnce();
        expect(formSubmittedSpy).not.toHaveBeenCalledOnce();
    });

    it('should not open confirmation modal if form is invalid', async () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        await component.submitForm(true);
        expect(modalSpy).not.toHaveBeenCalledOnce();
    });
});
