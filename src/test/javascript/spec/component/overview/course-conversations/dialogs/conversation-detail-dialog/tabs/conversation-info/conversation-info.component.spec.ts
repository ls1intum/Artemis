import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationInfoComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-info/conversation-info.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../../helpers/conversationExampleModels';
import { Course } from 'app/entities/course.model';
import { ChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { GroupChatDTO, isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { channelRegex } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { GenericUpdateTextPropertyDialogComponent } from 'app/overview/course-conversations/dialogs/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('ConversationInfoComponent with ' + activeConversation.type, () => {
        let component: ConversationInfoComponent;
        let fixture: ComponentFixture<ConversationInfoComponent>;
        const course = { id: 1 } as Course;
        const canChangeChannelProperties = jest.fn();
        const canChangeGroupChatProperties = jest.fn();
        let updateChannelSpy: jest.SpyInstance;
        let updateGroupChatSpy: jest.SpyInstance;
        const exampleUpdatedGroupChat = generateExampleGroupChatDTO({ name: 'updated' });
        const exampleUpdatedChannel = generateExampleChannelDTO({
            name: 'updated',
            description: 'updated',
            topic: 'updated',
        });

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [ConversationInfoComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
                providers: [MockProvider(ChannelService), MockProvider(GroupChatService), MockProvider(NgbModal), MockProvider(AlertService)],
            }).compileComponents();
        }));

        beforeEach(() => {
            canChangeChannelProperties.mockReturnValue(true);
            canChangeGroupChatProperties.mockReturnValue(true);
            fixture = TestBed.createComponent(ConversationInfoComponent);
            component = fixture.componentInstance;
            component.activeConversation = activeConversation;
            component.course = course;
            component.canChangeChannelProperties = canChangeChannelProperties;
            component.canChangeGroupChatProperties = canChangeGroupChatProperties;
            fixture.detectChanges();

            const channelService = TestBed.inject(ChannelService);
            const groupChatService = TestBed.inject(GroupChatService);
            updateChannelSpy = jest.spyOn(channelService, 'update');
            updateChannelSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedChannel })));
            updateGroupChatSpy = jest.spyOn(groupChatService, 'update');
            updateGroupChatSpy.mockReturnValue(of(new HttpResponse({ body: exampleUpdatedGroupChat })));
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
                fixture.detectChanges();
                checkThatActionButtonOfSectionDoesNotExistInTemplate('name');
                checkThatActionButtonOfSectionDoesNotExistInTemplate('topic');
                checkThatActionButtonOfSectionDoesNotExistInTemplate('description');
            }

            if (isGroupChatDTO(activeConversation)) {
                checkThatActionButtonOfSectionExistsInTemplate('name');
                canChangeGroupChatProperties.mockReturnValue(false);
                fixture.detectChanges();
                checkThatActionButtonOfSectionDoesNotExistInTemplate('name');
            }
        });

        it('should open the edit name dialog when the respective action button is clicked', fakeAsync(() => {
            if (isChannelDTO(activeConversation) || isGroupChatDTO(activeConversation)) {
                genericEditPropertyDialogTest('name', {
                    propertyName: 'name',
                    maxPropertyLength: 30,
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

        function genericEditPropertyDialogTest(sectionName: string, expectedComponentInstance: object) {
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
            fixture.detectChanges();

            button.click();
            tick();

            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(GenericUpdateTextPropertyDialogComponent, defaultSecondLayerDialogOptions);
                for (const [key, value] of Object.entries(expectedComponentInstance)) {
                    expect(mockModalRef.componentInstance[key]).toEqual(value);
                }
                if (isGroupChatDTO(activeConversation)) {
                    const expectedUpdateDTO = new GroupChatDTO();
                    expectedUpdateDTO[expectedComponentInstance['propertyName']] = 'updated';
                    expect(updateGroupChatSpy).toHaveBeenCalledOnce();
                    expect(updateGroupChatSpy).toHaveBeenCalledWith(course.id, activeConversation.id, expectedUpdateDTO);
                }

                if (isChannelDTO(activeConversation)) {
                    const expectedUpdateDTO = new ChannelDTO();
                    expectedUpdateDTO[expectedComponentInstance['propertyName']] = 'updated';
                    expect(updateChannelSpy).toHaveBeenCalledOnce();
                    expect(updateChannelSpy).toHaveBeenCalledWith(course.id, activeConversation.id, expectedUpdateDTO);
                }

                expect(activeConversation[expectedComponentInstance['propertyName']]).toBe('updated');
            });
        }
    });
});
