import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationSettingsComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-settings/conversation-settings.component';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from '../../../../helpers/conversationExampleModels';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import * as ConversationPermissionUtils from 'app/shared/metis/conversations/conversation-permissions.utils';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('ConversationSettingsComponent with ' + activeConversation.type, () => {
        let component: ConversationSettingsComponent;
        let fixture: ComponentFixture<ConversationSettingsComponent>;
        const course = { id: 1 } as Course;
        const canLeaveConversation = jest.spyOn(ConversationPermissionUtils, 'canLeaveConversation');
        const canChangeArchivalState = jest.spyOn(ConversationPermissionUtils, 'canChangeChannelArchivalState');
        const canDeleteChannel = jest.spyOn(ConversationPermissionUtils, 'canDeleteChannel');

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [ConversationSettingsComponent, MockDirective(DeleteButtonDirective), MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
                providers: [MockProvider(NgbModal), MockProvider(ChannelService), MockProvider(GroupChatService), MockProvider(AlertService)],
            }).compileComponents();
        }));

        beforeEach(() => {
            canChangeArchivalState.mockReturnValue(true);
            canLeaveConversation.mockReturnValue(true);
            canDeleteChannel.mockReturnValue(true);
            fixture = TestBed.createComponent(ConversationSettingsComponent);
            component = fixture.componentInstance;
            component.course = course;
            component.activeConversation = activeConversation;
            component.ngOnInit();
            fixture.detectChanges();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should show buttons only if user has the required permissions', () => {
            expect(fixture.nativeElement.querySelector('.leave-conversation')).toBeTruthy();
            canLeaveConversation.mockReturnValue(false);
            component.ngOnInit();
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.leave-conversation')).toBeFalsy();

            if (isChannelDTO(activeConversation)) {
                expect(fixture.nativeElement.querySelector('.archive')).toBeTruthy();
                expect(fixture.nativeElement.querySelector('.delete')).toBeTruthy();

                canChangeArchivalState.mockReturnValue(false);
                component.ngOnInit();
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.archive')).toBeFalsy();
                canDeleteChannel.mockReturnValue(false);
                component.ngOnInit();
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.delete')).toBeFalsy();
            } else {
                expect(fixture.nativeElement.querySelector('.archive')).toBeFalsy();
                expect(fixture.nativeElement.querySelector('.delete')).toBeFalsy();
            }
        });

        it('should call the correct service depending on conversation type when leave conversation is requested', () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const leaveConversationSpy = jest.spyOn(component.conversationLeave, 'emit');
                jest.spyOn(channelService, 'deregisterUsersFromChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                fixture.nativeElement.querySelector('.leave-conversation').click();

                expect(channelService.deregisterUsersFromChannel).toHaveBeenCalledOnce();
                expect(channelService.deregisterUsersFromChannel).toHaveBeenCalledWith(course.id, activeConversation.id);
                expect(leaveConversationSpy).toHaveBeenCalledOnce();
            }

            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const leaveConversationSpy = jest.spyOn(component.conversationLeave, 'emit');
                jest.spyOn(groupChatService, 'removeUsersFromGroupChat').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                fixture.nativeElement.querySelector('.leave-conversation').click();

                expect(groupChatService.removeUsersFromGroupChat).toHaveBeenCalledOnce();
                expect(groupChatService.removeUsersFromGroupChat).toHaveBeenCalledWith(course.id, activeConversation.id);
                expect(leaveConversationSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open archive dialog when button is pressed', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const archiveSpy = jest.spyOn(channelService, 'archive').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.archive');

                genericConfirmationDialogTest(archiveButton);
                fixture.whenStable().then(() => {
                    expect(archiveSpy).toHaveBeenCalledOnce();
                    expect(archiveSpy).toHaveBeenCalledWith(course.id, activeConversation.id);
                });
            }
        }));

        it('should open unarchive dialog when button is pressed', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                (component.activeConversation as ChannelDTO).isArchived = true;
                fixture.detectChanges();
                const channelService = TestBed.inject(ChannelService);
                const unarchivespy = jest.spyOn(channelService, 'unarchive').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.unarchive');

                genericConfirmationDialogTest(archiveButton);
                fixture.whenStable().then(() => {
                    expect(unarchivespy).toHaveBeenCalledOnce();
                    expect(unarchivespy).toHaveBeenCalledWith(course.id, activeConversation.id);
                });
            }
        }));

        it('should call delete channel when callback is called', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const deleteSpy = jest.spyOn(channelService, 'delete').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                component.deleteChannel();
                fixture.whenStable().then(() => {
                    expect(deleteSpy).toHaveBeenCalledOnce();
                    expect(deleteSpy).toHaveBeenCalledWith(course.id, activeConversation.id);
                });
            }
        }));

        function genericConfirmationDialogTest(button: HTMLElement) {
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    translationParameters: undefined,
                    translationKeys: undefined,
                    canBeUndone: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();

            button.click();
            tick();

            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
            });
        }
    });
});
