import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { Location } from '@angular/common';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { BehaviorSubject, EMPTY, Subject } from 'rxjs';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';

const examples: ConversationDTO[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE, subTypeReferenceId: 1 } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE, subTypeReferenceId: 1 } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM, subTypeReferenceId: 1 } as ChannelDTO),
];
examples.forEach((activeConversation) => {
    describe('ConversationHeaderComponent with' + +(activeConversation instanceof ChannelDTO ? activeConversation.subType + ' ' : '') + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationHeaderComponent;
        let fixture: ComponentFixture<ConversationHeaderComponent>;
        let metisConversationService: MetisConversationService;
        let location: Location;
        const course = { id: 1 } as any;
        const canAddUsers = vi.fn();

        beforeEach(async () => {
            vi.useFakeTimers();
            TestBed.configureTestingModule({
                imports: [MockComponent(ChannelIconComponent), MockComponent(ProfilePictureComponent), MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
                providers: [
                    provideRouter([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                    MockProvider(DialogService),
                    MockProvider(ConversationService),
                    { provide: MetisService, useClass: MockMetisService },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: MetisConversationService, useClass: MockMetisConversationService },
                    LocalStorageService,
                ],
            });
        });

        beforeEach(() => {
            canAddUsers.mockReturnValue(true);
            metisConversationService = TestBed.inject(MetisConversationService);
            Object.defineProperty(metisConversationService, 'course', { get: () => course });
            Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });
            Object.defineProperty(metisConversationService, 'forceRefresh', { value: () => EMPTY });

            location = TestBed.inject(Location);

            fixture = TestBed.createComponent(ConversationHeaderComponent);
            component = fixture.componentInstance;
            component.canAddUsers = canAddUsers;
            fixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.activeConversation).toEqual(activeConversation);
        });

        it('should open the add users dialog', () => {
            canAddUsers.mockReturnValue(false);
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsers.mockReturnValue(true);
            fixture.changeDetectorRef.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const dialogService = TestBed.inject(DialogService);
            const mockDialogRef = { onClose: new Subject(), close: vi.fn() } as unknown as DynamicDialogRef;
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            fixture.changeDetectorRef.detectChanges();
            addUsersButton.click();
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(ConversationAddUsersDialogComponent, expect.anything());
        });

        it('should open dialog details dialog with members tab', () => {
            detailDialogTest('members', ConversationDetailTabs.MEMBERS);
        });

        it('should open dialog details dialog with info tab', () => {
            detailDialogTest('info', ConversationDetailTabs.INFO);
        });

        it('should toggle search when search button is pressed', () => {
            const searchButton = fixture.debugElement.nativeElement.querySelector('.search');
            expect(searchButton).toBeTruthy();

            const toggleSearchSpy = vi.spyOn(component, 'toggleSearchBar');
            fixture.detectChanges();
            searchButton.click();
            expect(toggleSearchSpy).toHaveBeenCalledOnce();
        });

        it('should set otherUser to the non-requesting user in a one-to-one conversation', () => {
            const oneToOneChat = generateOneToOneChatDTO({});
            oneToOneChat.members = [
                { id: 1, isRequestingUser: true },
                { id: 2, isRequestingUser: false },
            ];

            component.activeConversation = oneToOneChat;
            component.getOtherUser();

            expect(component.otherUser).toEqual(oneToOneChat.members[1]);
        });

        it('should toggle pinned messages visibility', () => {
            const togglePinnedMessagesSpy = vi.spyOn(component, 'togglePinnedMessages');

            expect(component.showPinnedMessages).toBe(false);

            component.togglePinnedMessages();
            expect(togglePinnedMessagesSpy).toHaveBeenCalled();
            expect(component.showPinnedMessages).toBe(true);

            component.togglePinnedMessages();
            expect(component.showPinnedMessages).toBe(false);
        });

        it('should emit togglePinnedMessage event', () => {
            const togglePinnedMessageSpy = vi.spyOn(component.togglePinnedMessage, 'emit');

            component.togglePinnedMessages();
            expect(togglePinnedMessageSpy).toHaveBeenCalled();
        });

        it('should set showPinnedMessages to false if pinnedMessageCount changes to 0 while it is currently showing pinned messages', () => {
            component.showPinnedMessages = true;
            fixture.componentRef.setInput('pinnedMessageCount', 3);
            fixture.detectChanges();

            fixture.componentRef.setInput('pinnedMessageCount', 0);
            fixture.detectChanges();

            expect(component.showPinnedMessages).toBe(false);
        });

        it('should not change showPinnedMessages if pinnedMessageCount changes but is not 0', () => {
            component.showPinnedMessages = true;
            fixture.componentRef.setInput('pinnedMessageCount', 3);
            fixture.detectChanges();

            fixture.componentRef.setInput('pinnedMessageCount', 5);
            fixture.detectChanges();

            expect(component.showPinnedMessages).toBe(true);
        });

        it('should correctly handle first change of pinnedMessageCount', () => {
            component.showPinnedMessages = false;
            fixture.componentRef.setInput('pinnedMessageCount', 2);
            fixture.detectChanges();

            expect(component.showPinnedMessages).toBe(false);
        });

        if (activeConversation instanceof ChannelDTO && activeConversation.subType !== ChannelSubType.GENERAL) {
            it('should navigate to ' + activeConversation.subType, async () => {
                const button = fixture.debugElement.query(By.css('#subTypeReferenceRouterLink')).nativeElement;
                button.click();
                vi.advanceTimersByTime(0);
                vi.clearAllTimers();
                vi.useRealTimers();
                await fixture.whenStable();
                // Assert that the router has navigated to the correct link
                expect(location.path()).toBe('/courses/1/' + activeConversation.subType + 's/1');
            });
        }

        it('should open conversation detail dialog', () => {
            const dialogService = TestBed.inject(DialogService);
            const mockOnClose = new Subject<any>();
            const mockDialogRef = { onClose: mockOnClose.asObservable(), close: vi.fn() } as unknown as DynamicDialogRef;
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.INFO);
            vi.advanceTimersByTime(0);

            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, expect.anything());
        });

        it('should emit onUpdateSidebar when conversation detail dialog is closed', () => {
            const dialogService = TestBed.inject(DialogService);
            const mockOnClose = new Subject<any>();
            const mockDialogRef = { onClose: mockOnClose.asObservable(), close: vi.fn() } as unknown as DynamicDialogRef;
            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

            const onUpdateSidebarSpy = vi.spyOn(component.onUpdateSidebar, 'emit');

            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.INFO);
            mockOnClose.next(true);
            mockOnClose.complete();
            vi.advanceTimersByTime(0);

            expect(onUpdateSidebarSpy).toHaveBeenCalledOnce();
        });

        it('should emit collapseSearch when toggleSearchBar is called', () => {
            const collapseSearchSpy = vi.spyOn(component.onSearchClick, 'emit');
            component.toggleSearchBar();

            expect(collapseSearchSpy).toHaveBeenCalledOnce();
        });

        function detailDialogTest(className: string, tab: ConversationDetailTabs) {
            const detailButton = fixture.debugElement.nativeElement.querySelector('.' + className);
            expect(detailButton).toBeTruthy();

            const dialogService = TestBed.inject(DialogService);
            const mockOnClose = new Subject<any>();
            const mockDialogRef = { onClose: mockOnClose.asObservable(), close: vi.fn() } as unknown as DynamicDialogRef;
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            fixture.changeDetectorRef.detectChanges();
            detailButton.click();
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, expect.anything());
        }
    });
});
