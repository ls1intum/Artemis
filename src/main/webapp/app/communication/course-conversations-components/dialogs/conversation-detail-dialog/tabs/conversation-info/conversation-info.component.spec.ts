import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChatDTO, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { channelRegex } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { GenericUpdateTextPropertyDialogComponent } from 'app/communication/course-conversations-components/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConversationInfoComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-info/conversation-info.component';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRouteWithSubjects } from 'test/helpers/mocks/activated-route/mock-activated-route-with-subjects';
import * as globalUtils from 'app/shared/util/global.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationInfoComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationInfoComponent;
        let fixture: ComponentFixture<ConversationInfoComponent>;
        const course = { id: 1 } as Course;
        const canChangeChannelProperties = vi.fn();
        const canChangeGroupChatProperties = vi.fn();
        let updateChannelSpy: ReturnType<typeof vi.spyOn>;
        let updateGroupChatSpy: ReturnType<typeof vi.spyOn>;
        let updateIsMutedSpy: ReturnType<typeof vi.spyOn>;
        const exampleUpdatedGroupChat = generateExampleGroupChatDTO({ name: 'updated' });
        const exampleUpdatedChannel = generateExampleChannelDTO({
            name: 'updated',
            description: 'updated',
            topic: 'updated',
        } as ChannelDTO);

        beforeEach(async () => {
        vi.useFakeTimers();
            TestBed.configureTestingModule({
                imports: [ConversationInfoComponent, TranslateModule.forRoot(), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockDirective(TranslateDirective)],
                providers: [
                    MockProvider(ChannelService),
                    MockProvider(GroupChatService),
                    MockProvider(DialogService),
                    MockProvider(AlertService),
                    MockProvider(ConversationService),
                    MockProvider(CourseNotificationSettingService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: Router, useClass: MockRouter },
                    { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                ],
            });
        });

        beforeEach(() => {
            canChangeChannelProperties.mockReturnValue(true);
            canChangeGroupChatProperties.mockReturnValue(true);
            fixture = TestBed.createComponent(ConversationInfoComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('activeConversation', activeConversation);
            fixture.componentRef.setInput('course', course);
            component.canChangeChannelProperties = canChangeChannelProperties;
            component.canChangeGroupChatProperties = canChangeGroupChatProperties;

            // Mock CourseNotificationSettingService
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);
            vi.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
                of({
                    selectedPreset: 1,
                    notificationTypeChannels: {
                        conversationMessage: { WEBAPP: true, EMAIL: false, PUSH: false },
                        conversationMention: { WEBAPP: false, EMAIL: false, PUSH: false },
                    },
                }),
            );

            fixture.detectChanges();

            const channelService = TestBed.inject(ChannelService);
            const groupChatService = TestBed.inject(GroupChatService);
            const conversationService = TestBed.inject(ConversationService);
            updateChannelSpy = vi.spyOn(channelService, 'update');
            updateChannelSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedChannel })));
            updateGroupChatSpy = vi.spyOn(groupChatService, 'update');
            updateGroupChatSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedGroupChat })));
            updateIsMutedSpy = vi.spyOn(conversationService, 'updateIsMuted');
            updateIsMutedSpy.mockReturnValue(of(new HttpResponse({ body: undefined })));
        });

        afterEach(() => {
        vi.useRealTimers();
            vi.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should hide certain sections of the info component depending on the type of the conversation', () => {
            if (isChannelDTO(activeConversation)) {
                checkThatSectionExistsInTemplate('name');
                checkThatSectionExistsInTemplate('topic');
                checkThatSectionExistsInTemplate('description');
                checkThatSectionExistsInTemplate('moreinfo');
            }
            if (isGroupChatDTO(activeConversation)) {
                checkThatSectionExistsInTemplate('name');
                checkThatSectionDoesNotExistInTemplate('topic');
                checkThatSectionDoesNotExistInTemplate('description');
                checkThatSectionExistsInTemplate('moreinfo');
            }

            if (isOneToOneChatDTO(activeConversation)) {
                checkThatSectionDoesNotExistInTemplate('name');
                checkThatSectionDoesNotExistInTemplate('topic');
                checkThatSectionDoesNotExistInTemplate('description');
                checkThatSectionExistsInTemplate('moreinfo');
            }
        });

        it('should hide the action buttons if the user is missing the permissions', () => {
            if (isChannelDTO(activeConversation)) {
                checkThatActionButtonOfSectionExistsInTemplate('name');
                checkThatActionButtonOfSectionExistsInTemplate('topic');
                checkThatActionButtonOfSectionExistsInTemplate('description');

                canChangeChannelProperties.mockReturnValue(false);
                fixture.changeDetectorRef.detectChanges();
                checkThatActionButtonOfSectionDoesNotExistInTemplate('name');
                checkThatActionButtonOfSectionDoesNotExistInTemplate('topic');
                checkThatActionButtonOfSectionDoesNotExistInTemplate('description');
            }

            if (isGroupChatDTO(activeConversation)) {
                checkThatActionButtonOfSectionExistsInTemplate('name');
                canChangeGroupChatProperties.mockReturnValue(false);
                fixture.changeDetectorRef.detectChanges();
                checkThatActionButtonOfSectionDoesNotExistInTemplate('name');
            }
        });

        it('should open the edit name dialog when the respective action button is clicked', () => {
            if (isChannelDTO(activeConversation) || isGroupChatDTO(activeConversation)) {
                genericEditPropertyDialogTest('name', {
                    propertyName: 'name',
                    maxPropertyLength: 20,
                    isRequired: true,
                    regexPattern: channelRegex,
                });
            }
        });

        it('should open the edit topic dialog when the respective action button is clicked', () => {
            if (isChannelDTO(activeConversation)) {
                genericEditPropertyDialogTest('topic', {
                    propertyName: 'topic',
                    maxPropertyLength: 250,
                    isRequired: false,
                    regexPattern: undefined,
                });
            }
        });

        it('should open the edit description dialog when the respective action button is clicked', () => {
            if (isChannelDTO(activeConversation)) {
                genericEditPropertyDialogTest('description', {
                    propertyName: 'description',
                    maxPropertyLength: 250,
                    isRequired: false,
                    regexPattern: undefined,
                });
            }
        });

        it('should show notification section for all conversation types', () => {
            checkThatSectionExistsInTemplate('notification');
        });

        it('should emit correct mute state on toggle with debounce logic', () => {
            activeConversation.isMuted = false;
            component.onMuteToggle();
            vi.advanceTimersByTime(100);
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, activeConversation.id, true);

            activeConversation.isMuted = true;
            component.onMuteToggle();
            vi.advanceTimersByTime(100);
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, activeConversation.id, false);
        });

        it('should show correct notification message and link for ignored preset', () => {
            component['notificationSettings'] = { selectedPreset: 3, notificationTypeChannels: {} } as any;
            component['checkNotificationStatus']();
            fixture.changeDetectorRef.detectChanges();
            const link = fixture.nativeElement.querySelector('#notification-section a');
            expect(link).toBeTruthy();
        });

        it('should handle errors when toggling mute state', () => {
            updateIsMutedSpy.mockReturnValue(throwError(() => new Error('Test error')));
            const onErrorSpy = vi.spyOn(globalUtils, 'onError');
            component.onMuteToggle();
            vi.advanceTimersByTime(100);
            expect(onErrorSpy).toHaveBeenCalled();
        });

        it('should show enabled notification message', () => {
            component['notificationSettings'] = {
                selectedPreset: 1,
                notificationTypeChannels: {
                    conversationMessage: { WEBAPP: true, EMAIL: false, PUSH: false },
                    conversationMention: { WEBAPP: false, EMAIL: false, PUSH: false },
                },
            } as any;
            activeConversation.isMuted = false;
            component['checkNotificationStatus']();
            fixture.changeDetectorRef.detectChanges();

            expect(component.isNotificationsEnabled).toBeTruthy();
            const desc = fixture.nativeElement.querySelector('#notification-section .text-muted');
            expect(desc).toBeTruthy();

            const toggle = fixture.nativeElement.querySelector('#muteSwitch') as HTMLInputElement;
            expect(toggle.checked).toBe(false);
        });

        it('should show disabled notification message when course notifications are disabled', () => {
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);

            vi.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
                of({
                    selectedPreset: 3,
                    notificationTypeChannels: {
                        conversationMessage: { WEBAPP: false, EMAIL: false, PUSH: false },
                        conversationMention: { WEBAPP: false, EMAIL: false, PUSH: false },
                    },
                }),
            );

            activeConversation.isMuted = false;
            component['loadNotificationSettings']();
            vi.advanceTimersByTime(0); // Wait for the service response to be processed
            fixture.changeDetectorRef.detectChanges();

            expect(component.isNotificationsEnabled).toBeFalsy();
            const desc = fixture.nativeElement.querySelector('#notification-section .text-muted');
            expect(desc).toBeTruthy();
        });

        it('should show muted notification message when conversation is muted', () => {
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);
            vi.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
                of({
                    selectedPreset: 1,
                    notificationTypeChannels: {
                        conversationMessage: { WEBAPP: true, EMAIL: true, PUSH: true },
                        conversationMention: { WEBAPP: true, EMAIL: true, PUSH: true },
                    },
                }),
            );

            activeConversation.isMuted = true;
            component['loadNotificationSettings']();
            vi.advanceTimersByTime(0); // Wait for the service response to be processed
            fixture.changeDetectorRef.detectChanges();

            expect(component.isNotificationsEnabled).toBeTruthy();
            const desc = fixture.nativeElement.querySelector('#notification-section .text-muted');
            expect(desc).toBeTruthy();

            const toggle = fixture.nativeElement.querySelector('#muteSwitch') as HTMLInputElement;
            expect(toggle.checked).toBe(true);
        });

        it('should show error if loadNotificationSettings fails', () => {
            const error = new HttpErrorResponse({
                status: 400,
            });
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);
            const alertService = TestBed.inject(AlertService);
            const translateService = TestBed.inject(TranslateService);

            vi.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(throwError(() => error));
            vi.spyOn(alertService, 'error');
            vi.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

            component['loadNotificationSettings']();
            vi.advanceTimersByTime(0);

            expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        });

        it('should handle error when updating group chat', () => {
            if (isGroupChatDTO(activeConversation)) {
                const error = new HttpErrorResponse({
                    status: 400,
                });
                const groupChatService = TestBed.inject(GroupChatService);
                const alertService = TestBed.inject(AlertService);
                const translateService = TestBed.inject(TranslateService);

                vi.spyOn(groupChatService, 'update').mockReturnValue(throwError(() => error));
                vi.spyOn(alertService, 'error');
                vi.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

                component['updateGroupChat'](activeConversation, 'name', 'new name');
                vi.advanceTimersByTime(0);

                expect(alertService.error).toHaveBeenCalledWith('error.http.400');
            }
        });

        it('should not update group chat when course ID is not available', () => {
            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const updateSpy = vi.spyOn(groupChatService, 'update');

                fixture.componentRef.setInput('course', {} as Course);
                fixture.changeDetectorRef.detectChanges();
                component['updateGroupChat'](activeConversation, 'name', 'new name');

                expect(updateSpy).not.toHaveBeenCalled();
            }
        });

        it('should not update channel when course ID is not available', () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const updateSpy = vi.spyOn(channelService, 'update');

                fixture.componentRef.setInput('course', {} as Course);
                fixture.changeDetectorRef.detectChanges();
                component['updateChannel'](activeConversation, 'name', 'new name');

                expect(updateSpy).not.toHaveBeenCalled();
            }
        });

        it('should handle error when updating channel', () => {
            if (isChannelDTO(activeConversation)) {
                const error = new HttpErrorResponse({
                    status: 400,
                    error: { skipAlert: true },
                });
                const channelService = TestBed.inject(ChannelService);
                const alertService = TestBed.inject(AlertService);
                const translateService = TestBed.inject(TranslateService);

                vi.spyOn(channelService, 'update').mockReturnValue(throwError(() => error));
                vi.spyOn(alertService, 'error');
                vi.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

                component['updateChannel'](activeConversation, 'name', 'new name');
                vi.advanceTimersByTime(0);

                expect(alertService.error).toHaveBeenCalledWith('error.http.400');
            }
        });

        function checkThatActionButtonOfSectionExistsInTemplate(sectionName: string) {
            const actionButtonElement = fixture.nativeElement.querySelector(`#${sectionName}-section .action-button`);
            expect(actionButtonElement).toBeTruthy();
        }

        function checkThatActionButtonOfSectionDoesNotExistInTemplate(sectionName: string) {
            const actionButtonElement = fixture.nativeElement.querySelector(`#${sectionName}-section .action-button`);
            expect(actionButtonElement).toBeFalsy();
        }

        function checkThatSectionExistsInTemplate(sectionName: string) {
            const section = fixture.debugElement.nativeElement.querySelector(`#${sectionName}-section`);
            expect(section).toBeTruthy();
        }

        function checkThatSectionDoesNotExistInTemplate(sectionName: string) {
            const section = fixture.debugElement.nativeElement.querySelector(`#${sectionName}-section`);
            expect(section).toBeFalsy();
        }

        function genericEditPropertyDialogTest(sectionName: string, expectedComponentInstance: any) {
            const button = fixture.nativeElement.querySelector(`#${sectionName}-section .action-button`);
            const dialogService = TestBed.inject(DialogService);
            const mockOnClose = new Subject<any>();
            const mockDialogRef = {
                onClose: mockOnClose.asObservable(),
                close: vi.fn(),
            };
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef as unknown as DynamicDialogRef);
            fixture.changeDetectorRef.detectChanges();

            button.click();
            mockOnClose.next('updated');
            mockOnClose.complete();
            vi.advanceTimersByTime(0);

            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(GenericUpdateTextPropertyDialogComponent, expect.anything());

                if (isGroupChatDTO(activeConversation)) {
                    const expectedUpdateDTO = new GroupChatDTO();
                    (expectedUpdateDTO as any)[expectedComponentInstance['propertyName']] = 'updated';
                    expect(updateGroupChatSpy).toHaveBeenCalledOnce();
                    expect(updateGroupChatSpy).toHaveBeenCalledWith(course.id, activeConversation.id, expectedUpdateDTO);
                }

                if (isChannelDTO(activeConversation)) {
                    const expectedUpdateDTO = new ChannelDTO();
                    (expectedUpdateDTO as any)[expectedComponentInstance['propertyName']] = 'updated';
                    expect(updateChannelSpy).toHaveBeenCalledOnce();
                    expect(updateChannelSpy).toHaveBeenCalledWith(course.id, activeConversation.id, expectedUpdateDTO);
                }

                expect((activeConversation as any)[expectedComponentInstance['propertyName']]).toBe('updated');
            });
        }
    });
});
