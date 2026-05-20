import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import * as ConversationPermissionUtils from 'app/communication/conversations/conversation-permissions.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConversationSettingsComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-settings/conversation-settings.component';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationSettingsComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationSettingsComponent;
        let fixture: ComponentFixture<ConversationSettingsComponent>;
        const course = { id: 1 } as Course;
        const canLeaveConversation = vi.spyOn(ConversationPermissionUtils, 'canLeaveConversation');
        const canChangeArchivalState = vi.spyOn(ConversationPermissionUtils, 'canChangeChannelArchivalState');
        const canDeleteChannel = vi.spyOn(ConversationPermissionUtils, 'canDeleteChannel');
        let modalServiceOpen: ReturnType<typeof vi.spyOn>;

        beforeEach(async () => {
            TestBed.configureTestingModule({
                imports: [FaIconComponent, ConversationSettingsComponent, MockDirective(DeleteButtonDirective), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [
                    MockProvider(DialogService),
                    MockProvider(ChannelService),
                    MockProvider(GroupChatService),
                    MockProvider(AlertService),
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            });
        });

        beforeEach(() => {
            canChangeArchivalState.mockReturnValue(true);
            canLeaveConversation.mockReturnValue(true);
            canDeleteChannel.mockReturnValue(true);
            fixture = TestBed.createComponent(ConversationSettingsComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('activeConversation', activeConversation);
            const dialogService = TestBed.inject(DialogService);
            modalServiceOpen = vi.spyOn(dialogService, 'open').mockReturnValue({
                onClose: new Subject().asObservable(),
                close: vi.fn(),
            } as unknown as DynamicDialogRef);

            fixture.detectChanges();
            component.ngOnInit();
        });

        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
            // Reset injection context
            TestBed.resetTestingModule();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should show buttons only if user has the required permissions', () => {
            expect(fixture.nativeElement.querySelector('.leave-conversation')).toBeTruthy();
            canLeaveConversation.mockReturnValue(false);
            component.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('.leave-conversation')).toBeFalsy();

            if (isChannelDTO(activeConversation)) {
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeTruthy();
                expect(fixture.nativeElement.querySelector('.delete')).toBeTruthy();

                canChangeArchivalState.mockReturnValue(false);
                component.ngOnInit();
                fixture.changeDetectorRef.detectChanges();
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeFalsy();
                canDeleteChannel.mockReturnValue(false);
                component.ngOnInit();
                fixture.changeDetectorRef.detectChanges();
                expect(fixture.nativeElement.querySelector('.delete')).toBeFalsy();
            } else {
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeFalsy();
                expect(fixture.nativeElement.querySelector('.delete')).toBeFalsy();
            }
        });

        it('should call the correct service depending on conversation type when leave conversation is requested', () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const leaveConversationSpy = vi.spyOn(component.conversationLeave, 'emit');
                vi.spyOn(channelService, 'deregisterUsersFromChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                fixture.nativeElement.querySelector('.leave-conversation').click();

                expect(channelService.deregisterUsersFromChannel).toHaveBeenCalledOnce();
                expect(channelService.deregisterUsersFromChannel).toHaveBeenCalledWith(course.id, activeConversation.id);
                expect(leaveConversationSpy).toHaveBeenCalledOnce();
            }

            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const leaveConversationSpy = vi.spyOn(component.conversationLeave, 'emit');
                vi.spyOn(groupChatService, 'removeUsersFromGroupChat').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                fixture.nativeElement.querySelector('.leave-conversation').click();

                expect(groupChatService.removeUsersFromGroupChat).toHaveBeenCalledOnce();
                expect(groupChatService.removeUsersFromGroupChat).toHaveBeenCalledWith(course.id, activeConversation.id);
                expect(leaveConversationSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open archive dialog when button is pressed', async () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const archiveSpy = vi.spyOn(channelService, 'archive').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.archive-toggle');

                genericConfirmationDialogTest(archiveButton);
                await fixture.whenStable();
                expect(archiveSpy).toHaveBeenCalledOnce();
                expect(archiveSpy).toHaveBeenCalledWith(course.id, activeConversation.id);
            }
        });

        it('should open unarchive dialog when button is pressed', async () => {
            if (isChannelDTO(activeConversation)) {
                const activeConversationCopy = component.activeConversation();
                (activeConversationCopy as ChannelDTO).isArchived = true;
                fixture.componentRef.setInput('activeConversation', activeConversationCopy as ConversationDTO);
                fixture.changeDetectorRef.detectChanges();
                const channelService = TestBed.inject(ChannelService);
                const unarchivespy = vi.spyOn(channelService, 'unarchive').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.archive-toggle');

                genericConfirmationDialogTest(archiveButton);
                await fixture.whenStable();
                expect(unarchivespy).toHaveBeenCalledOnce();
                expect(unarchivespy).toHaveBeenCalledWith(course.id, activeConversation.id);
            }
        });

        it('should call delete channel when callback is called', async () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const deleteSpy = vi.spyOn(channelService, 'delete').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                component.deleteChannel();
                await fixture.whenStable();
                expect(deleteSpy).toHaveBeenCalledOnce();
                expect(deleteSpy).toHaveBeenCalledWith(course.id, activeConversation.id);
            }
        });

        it('should toggle channel privacy, update conversationAsChannel, and emit channelPrivacyChange', () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const toggleSpy = vi
                    .spyOn(channelService, 'toggleChannelPrivacy')
                    .mockReturnValue(of(new HttpResponse<ChannelDTO>({ body: { ...activeConversation, isPublic: false } })));

                const privacyChangeSpy = vi.spyOn(component.channelPrivacyChange, 'emit');

                const dialogCloseSubject = new Subject();
                const dialogService = TestBed.inject(DialogService);
                vi.spyOn(dialogService, 'open').mockReturnValue({
                    onClose: dialogCloseSubject.asObservable(),
                    close: vi.fn(),
                } as unknown as DynamicDialogRef);

                component.toggleChannelPrivacy();
                dialogCloseSubject.next(true);
                vi.advanceTimersByTime(0);

                expect(toggleSpy).toHaveBeenCalledOnce();
                expect(toggleSpy).toHaveBeenCalledWith(course.id, activeConversation.id);

                expect(component.conversationAsChannel!.isPublic).toBe(false);
                expect(privacyChangeSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open the public channel modal if channel is currently private', () => {
            if (isChannelDTO(activeConversation)) {
                const conversation = component.activeConversation();
                (conversation as ChannelDTO).isPublic = false;
                fixture.componentRef.setInput('activeConversation', conversation as ConversationDTO);
                component.ngOnInit();
                fixture.changeDetectorRef.detectChanges();

                const channelService = TestBed.inject(ChannelService);
                vi.spyOn(channelService, 'toggleChannelPrivacy').mockReturnValue(of(new HttpResponse<ChannelDTO>({ body: { ...activeConversation, isPublic: true } })));

                component.toggleChannelPrivacy();
                vi.advanceTimersByTime(0);
                fixture.changeDetectorRef.detectChanges();

                expect(modalServiceOpen).toHaveBeenCalledOnce();
                expect(modalServiceOpen).toHaveBeenCalledWith(GenericConfirmationDialogComponent, expect.objectContaining(defaultSecondLayerDialogOptions));
            }
        });

        it('should open the private channel modal if channel is currently public', () => {
            if (isChannelDTO(activeConversation)) {
                const conversation = component.activeConversation();
                (conversation as ChannelDTO).isPublic = true;
                fixture.componentRef.setInput('activeConversation', conversation as ConversationDTO);
                component.ngOnInit();
                fixture.changeDetectorRef.detectChanges();

                const channelService = TestBed.inject(ChannelService);
                vi.spyOn(channelService, 'toggleChannelPrivacy').mockReturnValue(of(new HttpResponse<ChannelDTO>({ body: { ...activeConversation, isPublic: false } })));

                component.toggleChannelPrivacy();
                vi.advanceTimersByTime(0);
                fixture.changeDetectorRef.detectChanges();

                expect(modalServiceOpen).toHaveBeenCalledOnce();
                expect(modalServiceOpen).toHaveBeenCalledWith(GenericConfirmationDialogComponent, expect.objectContaining(defaultSecondLayerDialogOptions));
            }
        });

        function genericConfirmationDialogTest(button: HTMLElement) {
            const dialogService = TestBed.inject(DialogService);
            const dialogCloseSubject = new Subject<any>();
            const mockDialogRef = {
                onClose: dialogCloseSubject.asObservable(),
                close: vi.fn(),
            };
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef as unknown as DynamicDialogRef);
            fixture.detectChanges();

            button.click();
            vi.advanceTimersByTime(0);

            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(GenericConfirmationDialogComponent, expect.anything());

            // Emit a truthy value to trigger the component's onClose callback
            dialogCloseSubject.next(true);
            vi.advanceTimersByTime(0);
        }
    });
});
