import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackDetailChannelModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-detail-channel/feedback-detail-channel-modal.component';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

describe('FeedbackDetailChannelModalComponent', () => {
    setupTestBed({ zoneless: true });

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
        component.isConfirmModalOpen.set(false);
        fixture.detectChanges();
    });

    it('should initialize form and inputs', () => {
        expect(component.feedbackDetail().detailTexts).toStrictEqual(['Sample feedback']);
        expect(component.form).toBeDefined();
        expect(component.form.valid).toBe(false);
    });

    it('should call activeModal.close when closeModal is triggered', () => {
        const closeSpy = vi.spyOn(activeModal, 'close');
        component.closeModal();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should call activeModal.dismiss when dismissModal is triggered', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        component.dismissModal();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should open confirmation modal and emit formSubmitted on successful confirmation', async () => {
        vi.spyOn(component, 'handleModal').mockResolvedValue(true);

        component.form.setValue({
            name: 'channel',
            description: 'channelDescription',
            isPrivate: true,
            isAnnouncementChannel: false,
        });

        const formSubmittedSpy = vi.spyOn(component.formSubmitted, 'emit');
        await component.submitForm(false);

        expect(component.isConfirmModalOpen()).toBe(false);
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
        vi.spyOn(component, 'handleModal').mockResolvedValue(true);
        const formSubmittedSpy = vi.spyOn(component.formSubmitted, 'emit');

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
        vi.spyOn(component, 'handleModal').mockResolvedValue(false);

        const formSubmittedSpy = vi.spyOn(component.formSubmitted, 'emit');

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
        const modalSpy = vi.spyOn(modalService, 'open');
        await component.submitForm(true);
        expect(modalSpy).not.toHaveBeenCalledOnce();
    });
});
