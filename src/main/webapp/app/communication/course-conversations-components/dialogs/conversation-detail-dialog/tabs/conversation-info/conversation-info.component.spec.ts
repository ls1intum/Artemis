import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
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
import { defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
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
        let component: ConversationInfoComponent;
        let fixture: ComponentFixture<ConversationInfoComponent>;
        const course = { id: 1 } as Course;
        const canChangeChannelProperties = jest.fn();
        const canChangeGroupChatProperties = jest.fn();
        let updateChannelSpy: jest.SpyInstance;
        let updateGroupChatSpy: jest.SpyInstance;
        let updateIsMutedSpy: jest.SpyInstance;
        const exampleUpdatedGroupChat = generateExampleGroupChatDTO({ name: 'updated' });
        const exampleUpdatedChannel = generateExampleChannelDTO({
            name: 'updated',
            description: 'updated',
            topic: 'updated',
        } as ChannelDTO);

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ConversationInfoComponent, TranslateModule.forRoot(), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockDirective(TranslateDirective)],
                providers: [
                    MockProvider(ChannelService),
                    MockProvider(GroupChatService),
                    MockProvider(NgbModal),
                    MockProvider(AlertService),
                    MockProvider(ConversationService),
                    MockProvider(CourseNotificationSettingService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: Router, useClass: MockRouter },
                    { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                ],
            }).compileComponents();
        }));

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
            jest.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
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
            updateChannelSpy = jest.spyOn(channelService, 'update');
            updateChannelSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedChannel })));
            updateGroupChatSpy = jest.spyOn(groupChatService, 'update');
            updateGroupChatSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedGroupChat })));
            updateIsMutedSpy = jest.spyOn(conversationService, 'updateIsMuted');
            updateIsMutedSpy.mockReturnValue(of(new HttpResponse({ body: undefined })));
        });

        afterEach(() => {
            jest.restoreAllMocks();
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

        it('should open the edit name dialog when the respective action button is clicked', fakeAsync(() => {
            if (isChannelDTO(activeConversation) || isGroupChatDTO(activeConversation)) {
                genericEditPropertyDialogTest('name', {
                    propertyName: 'name',
                    maxPropertyLength: 20,
                    isRequired: true,
                    regexPattern: channelRegex,
                });
            }
        }));

        it('should open the edit topic dialog when the respective action button is clicked', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                genericEditPropertyDialogTest('topic', {
                    propertyName: 'topic',
                    maxPropertyLength: 250,
                    isRequired: false,
                    regexPattern: undefined,
                });
            }
        }));

        it('should open the edit description dialog when the respective action button is clicked', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                genericEditPropertyDialogTest('description', {
                    propertyName: 'description',
                    maxPropertyLength: 250,
                    isRequired: false,
                    regexPattern: undefined,
                });
            }
        }));

        it('should show notification section for all conversation types', () => {
            checkThatSectionExistsInTemplate('notification');
        });

        it('should emit correct mute state on toggle with debounce logic', fakeAsync(() => {
            activeConversation.isMuted = false;
            component.onMuteToggle();
            tick(100);
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, activeConversation.id, true);

            activeConversation.isMuted = true;
            component.onMuteToggle();
            tick(100);
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, activeConversation.id, false);
        }));

        it('should show correct notification message and link for ignored preset', () => {
            component['notificationSettings'] = { selectedPreset: 3, notificationTypeChannels: {} } as any;
            component['checkNotificationStatus']();
            fixture.changeDetectorRef.detectChanges();
            const link = fixture.nativeElement.querySelector('#notification-section a');
            expect(link).toBeTruthy();
        });

        it('should handle errors when toggling mute state', fakeAsync(() => {
            updateIsMutedSpy.mockReturnValue(throwError(() => new Error('Test error')));
            const onErrorSpy = jest.spyOn(globalUtils, 'onError');
            component.onMuteToggle();
            tick(100);
            expect(onErrorSpy).toHaveBeenCalled();
        }));

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
            expect(toggle.checked).toBeFalse();
        });

        it('should show disabled notification message when course notifications are disabled', fakeAsync(() => {
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);

            jest.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
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
            tick(); // Wait for the service response to be processed
            fixture.changeDetectorRef.detectChanges();

            expect(component.isNotificationsEnabled).toBeFalsy();
            const desc = fixture.nativeElement.querySelector('#notification-section .text-muted');
            expect(desc).toBeTruthy();
        }));

        it('should show muted notification message when conversation is muted', fakeAsync(() => {
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);
            jest.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(
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
            tick(); // Wait for the service response to be processed
            fixture.changeDetectorRef.detectChanges();

            expect(component.isNotificationsEnabled).toBeTruthy();
            const desc = fixture.nativeElement.querySelector('#notification-section .text-muted');
            expect(desc).toBeTruthy();

            const toggle = fixture.nativeElement.querySelector('#muteSwitch') as HTMLInputElement;
            expect(toggle.checked).toBeTrue();
        }));

        it('should show error if loadNotificationSettings fails', fakeAsync(() => {
            const error = new HttpErrorResponse({
                status: 400,
            });
            const notificationSettingService = TestBed.inject(CourseNotificationSettingService);
            const alertService = TestBed.inject(AlertService);
            const translateService = TestBed.inject(TranslateService);

            jest.spyOn(notificationSettingService, 'getSettingInfo').mockReturnValue(throwError(() => error));
            jest.spyOn(alertService, 'error');
            jest.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

            component['loadNotificationSettings']();
            tick();

            expect(alertService.error).toHaveBeenCalledWith('error.http.400');
        }));

        it('should handle error when updating group chat', fakeAsync(() => {
            if (isGroupChatDTO(activeConversation)) {
                const error = new HttpErrorResponse({
                    status: 400,
                });
                const groupChatService = TestBed.inject(GroupChatService);
                const alertService = TestBed.inject(AlertService);
                const translateService = TestBed.inject(TranslateService);

                jest.spyOn(groupChatService, 'update').mockReturnValue(throwError(() => error));
                jest.spyOn(alertService, 'error');
                jest.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

                component['updateGroupChat'](activeConversation, 'name', 'new name');
                tick();

                expect(alertService.error).toHaveBeenCalledWith('error.http.400');
            }
        }));

        it('should not update group chat when course ID is not available', () => {
            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const updateSpy = jest.spyOn(groupChatService, 'update');

                fixture.componentRef.setInput('course', {} as Course);
                fixture.changeDetectorRef.detectChanges();
                component['updateGroupChat'](activeConversation, 'name', 'new name');

                expect(updateSpy).not.toHaveBeenCalled();
            }
        });

        it('should not update channel when course ID is not available', () => {
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const updateSpy = jest.spyOn(channelService, 'update');

                fixture.componentRef.setInput('course', {} as Course);
                fixture.changeDetectorRef.detectChanges();
                component['updateChannel'](activeConversation, 'name', 'new name');

                expect(updateSpy).not.toHaveBeenCalled();
            }
        });

        it('should handle error when updating channel', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                const error = new HttpErrorResponse({
                    status: 400,
                    error: { skipAlert: true },
                });
                const channelService = TestBed.inject(ChannelService);
                const alertService = TestBed.inject(AlertService);
                const translateService = TestBed.inject(TranslateService);

                jest.spyOn(channelService, 'update').mockReturnValue(throwError(() => error));
                jest.spyOn(alertService, 'error');
                jest.spyOn(translateService, 'instant').mockReturnValue('error.http.400');

                component['updateChannel'](activeConversation, 'name', 'new name');
                tick();

                expect(alertService.error).toHaveBeenCalledWith('error.http.400');
            }
        }));

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
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    propertyName: undefined,
                    maxPropertyLength: undefined,
                    translationKeys: undefined,
                    isRequired: undefined,
                    regexPattern: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve('updated'),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.changeDetectorRef.detectChanges();

            button.click();
            tick();

            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(GenericUpdateTextPropertyDialogComponent, defaultSecondLayerDialogOptions);
                for (const [key, value] of Object.entries(expectedComponentInstance)) {
                    expect(mockModalRef.componentInstance[key as keyof typeof mockModalRef.componentInstance]).toEqual(value);
                }
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
