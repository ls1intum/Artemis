import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalOptions, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
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
import { input, runInInjectionContext } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConversationSettingsComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-settings/conversation-settings.component';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationSettingsComponent with ' + activeConversation.type, () => {
        let component: ConversationSettingsComponent;
        let fixture: ComponentFixture<ConversationSettingsComponent>;
        const course = { id: 1 } as Course;
        const canLeaveConversation = jest.spyOn(ConversationPermissionUtils, 'canLeaveConversation');
        const canChangeArchivalState = jest.spyOn(ConversationPermissionUtils, 'canChangeChannelArchivalState');
        const canDeleteChannel = jest.spyOn(ConversationPermissionUtils, 'canDeleteChannel');
        let modalServiceOpen: jest.SpyInstance<NgbModalRef, [content: any, options?: NgbModalOptions | undefined], any>;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [FaIconComponent],
                declarations: [ConversationSettingsComponent, MockDirective(DeleteButtonDirective), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [MockProvider(NgbModal), MockProvider(ChannelService), MockProvider(GroupChatService), MockProvider(AlertService)],
            }).compileComponents();
        }));

        beforeEach(() => {
            canChangeArchivalState.mockReturnValue(true);
            canLeaveConversation.mockReturnValue(true);
            canDeleteChannel.mockReturnValue(true);
            fixture = TestBed.createComponent(ConversationSettingsComponent);
            component = fixture.componentInstance;
            TestBed.runInInjectionContext(() => {
                component.course = input<Course>(course);
                component.activeConversation = input<ConversationDTO>(activeConversation);
                component.ngOnInit();
            });
            const modalService = TestBed.inject(NgbModal);
            modalServiceOpen = jest.spyOn(modalService, 'open').mockReturnValue({
                componentInstance: {
                    translationParameters: undefined,
                    translationKeys: undefined,
                    canBeUndone: false,
                    initialize: () => {},
                },
                result: Promise.resolve(),
            } as unknown as NgbModalRef);

            fixture.detectChanges();
        });

        afterEach(() => {
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
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.leave-conversation')).toBeFalsy();

            if (isChannelDTO(activeConversation)) {
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeTruthy();
                expect(fixture.nativeElement.querySelector('.delete')).toBeTruthy();

                canChangeArchivalState.mockReturnValue(false);
                component.ngOnInit();
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeFalsy();
                canDeleteChannel.mockReturnValue(false);
                component.ngOnInit();
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.delete')).toBeFalsy();
            } else {
                expect(fixture.nativeElement.querySelector('.archive-toggle')).toBeFalsy();
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
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.archive-toggle');

                genericConfirmationDialogTest(archiveButton);
                fixture.whenStable().then(() => {
                    expect(archiveSpy).toHaveBeenCalledOnce();
                    expect(archiveSpy).toHaveBeenCalledWith(course.id, activeConversation.id);
                });
            }
        }));

        it('should open unarchive dialog when button is pressed', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                runInInjectionContext(fixture.debugElement.injector, () => {
                    const activeConversation = component.activeConversation();
                    (activeConversation as ChannelDTO).isArchived = true;
                    component.activeConversation = input<ConversationDTO>(activeConversation as ConversationDTO);
                });
                fixture.detectChanges();
                const channelService = TestBed.inject(ChannelService);
                const unarchivespy = jest.spyOn(channelService, 'unarchive').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                const archiveButton = fixture.debugElement.nativeElement.querySelector('.archive-toggle');

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

        it('should toggle channel privacy, update conversationAsChannel, and emit channelPrivacyChange', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const toggleSpy = jest
                    .spyOn(channelService, 'toggleChannelPrivacy')
                    .mockReturnValue(of(new HttpResponse<ChannelDTO>({ body: Object.assign({}, activeConversation, { isPublic: false }) })));

                const privacyChangeSpy = jest.spyOn(component.channelPrivacyChange, 'emit');

                component.toggleChannelPrivacy();
                tick();

                expect(toggleSpy).toHaveBeenCalledOnce();
                expect(toggleSpy).toHaveBeenCalledWith(course.id, activeConversation.id);

                expect(component.conversationAsChannel!.isPublic).toBeFalse();
                expect(privacyChangeSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the public channel modal if channel is currently private', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                runInInjectionContext(fixture.debugElement.injector, () => {
                    const conversation = component.activeConversation();
                    (conversation as ChannelDTO).isPublic = false;
                    component.activeConversation = input<ConversationDTO>(conversation as ConversationDTO);
                });
                fixture.detectChanges();

                const channelService = TestBed.inject(ChannelService);
                jest.spyOn(channelService, 'toggleChannelPrivacy').mockReturnValue(
                    of(new HttpResponse<ChannelDTO>({ body: Object.assign({}, activeConversation, { isPublic: true }) })),
                );

                component.toggleChannelPrivacy();
                tick();

                expect(modalServiceOpen).toHaveBeenCalledOnce();
                expect(modalServiceOpen).toHaveBeenCalledWith(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
            }
        }));

        it('should open the private channel modal if channel is currently public', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                runInInjectionContext(fixture.debugElement.injector, () => {
                    const conversation = component.activeConversation();
                    (conversation as ChannelDTO).isPublic = true;
                    component.activeConversation = input<ConversationDTO>(conversation as ConversationDTO);
                });
                fixture.detectChanges();

                const channelService = TestBed.inject(ChannelService);
                jest.spyOn(channelService, 'toggleChannelPrivacy').mockReturnValue(
                    of(new HttpResponse<ChannelDTO>({ body: Object.assign({}, activeConversation, { isPublic: false }) })),
                );

                component.toggleChannelPrivacy();
                tick();

                expect(modalServiceOpen).toHaveBeenCalledOnce();
                expect(modalServiceOpen).toHaveBeenCalledWith(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
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
