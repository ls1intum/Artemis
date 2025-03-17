import { Course } from 'app/entities/course.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations/other/channel-icon/channel-icon.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { initializeDialog } from '../dialog-test-helpers';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockSyncStorage } from '../../../../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationDetailDialogComponent with ' + activeConversation.type, () => {
        let component: ConversationDetailDialogComponent;
        let fixture: ComponentFixture<ConversationDetailDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [ConversationDetailDialogComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ChannelIconComponent), MockDirective(TranslateDirective)],
                imports: [FontAwesomeModule],
                providers: [
                    MockProvider(NgbActiveModal),
                    MockProvider(ConversationService),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ConversationDetailDialogComponent);
                    component = fixture.componentInstance;
                    initializeDialog(component, fixture, { course, activeConversation, selectedTab: ConversationDetailTabs.INFO });
                    fixture.detectChanges();
                });
        }));

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.isInitialized).toBeTrue();
        });

        it('should not show the settings tab for one-to-one chats', () => {
            if (component.isOneToOneChat) {
                expect(fixture.nativeElement.querySelector('.settings-tab')).toBeFalsy();
            } else {
                expect(fixture.nativeElement.querySelector('.settings-tab')).toBeTruthy();
            }
        });

        it('should react correctly to events from members tab', () => {
            component.selectedTab = ConversationDetailTabs.MEMBERS;
            fixture.detectChanges();

            const membersComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-members'));
            expect(membersComponentDebug).toBeTruthy();

            const membersComponent = membersComponentDebug.componentInstance;
            expect(membersComponent).toBeTruthy();
            expect(component.changesWerePerformed).toBeFalse();

            membersComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBeTrue();
        });

        it('should react correctly to events from info tab', () => {
            component.selectedTab = ConversationDetailTabs.INFO;
            fixture.detectChanges();

            const infoComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-info'));
            expect(infoComponentDebug).toBeTruthy();

            const infoComponent = infoComponentDebug.componentInstance;
            expect(infoComponent).toBeTruthy();
            expect(component.changesWerePerformed).toBeFalse();

            infoComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBeTrue();
        });

        it('should react correctly to events from settings tab', () => {
            if (!component.isOneToOneChat) {
                component.selectedTab = ConversationDetailTabs.SETTINGS;
                fixture.detectChanges();

                const settingsComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-settings'));
                expect(settingsComponentDebug).toBeTruthy();

                const settingsComponent = settingsComponentDebug.componentInstance;
                expect(settingsComponent).toBeTruthy();

                const activeModal = TestBed.inject(NgbActiveModal);
                const closeSpy = jest.spyOn(activeModal, 'close');

                settingsComponent.channelArchivalChange.emit();
                expect(closeSpy).toHaveBeenCalledOnce();

                closeSpy.mockClear();
                settingsComponent.channelDeleted.emit();
                expect(closeSpy).toHaveBeenCalledOnce();

                closeSpy.mockClear();
                settingsComponent.conversationLeave.emit();
                expect(closeSpy).toHaveBeenCalledOnce();
            }
        });

        it('should mark changes and close the dialog on privacy/archival/channelDeleted/leave events', () => {
            const activeModal = TestBed.inject(NgbActiveModal);
            const closeSpy = jest.spyOn(activeModal, 'close');
            const dismissSpy = jest.spyOn(activeModal, 'dismiss');

            expect(component.changesWerePerformed).toBeFalse();

            component.onPrivacyChange();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledTimes(1);
            expect(dismissSpy).not.toHaveBeenCalled();

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onArchivalChange();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledTimes(1);
            expect(dismissSpy).not.toHaveBeenCalled();

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onChannelDeleted();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledTimes(1);

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onConversationLeave();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledTimes(1);
            expect(dismissSpy).not.toHaveBeenCalled();
        });

        it('should emit userNameClicked event when onUserNameClicked is called', () => {
            const testUserId = 42;
            const spy = jest.spyOn(component.userNameClicked, 'emit');
            component.onUserNameClicked(testUserId);
            expect(spy).toHaveBeenCalledWith(testUserId);
        });
    });
});
