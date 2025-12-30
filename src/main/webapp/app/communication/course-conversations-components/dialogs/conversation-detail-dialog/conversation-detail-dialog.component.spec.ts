import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRouteWithSubjects } from 'test/helpers/mocks/activated-route/mock-activated-route-with-subjects';

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
                    SessionStorageService,
                    { provide: Router, useClass: MockRouter },
                    { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ConversationDetailDialogComponent);
                    component = fixture.componentInstance;
                    initializeDialog(component, fixture, { course, activeConversation, selectedTab: ConversationDetailTabs.INFO });
                    fixture.changeDetectorRef.detectChanges();
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
            fixture.changeDetectorRef.detectChanges();

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
            fixture.changeDetectorRef.detectChanges();

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
                fixture.changeDetectorRef.detectChanges();

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
            expect(closeSpy).toHaveBeenCalledOnce();
            expect(dismissSpy).not.toHaveBeenCalled();

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onArchivalChange();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledOnce();
            expect(dismissSpy).not.toHaveBeenCalled();

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onChannelDeleted();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledOnce();

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onConversationLeave();
            expect(component.changesWerePerformed).toBeTrue();
            expect(closeSpy).toHaveBeenCalledOnce();
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
