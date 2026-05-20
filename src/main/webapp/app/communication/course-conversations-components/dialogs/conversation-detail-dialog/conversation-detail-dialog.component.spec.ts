import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
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
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationDetailDialogComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationDetailDialogComponent;
        let fixture: ComponentFixture<ConversationDetailDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(async () => {
            TestBed.configureTestingModule({
                imports: [
                    FontAwesomeModule,
                    ConversationDetailDialogComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ChannelIconComponent),
                    MockDirective(TranslateDirective),
                ],
                providers: [
                    { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                    { provide: DynamicDialogConfig, useValue: { data: {} } },
                    MockProvider(ConversationService),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    SessionStorageService,
                    { provide: Router, useClass: MockRouter },
                    { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                    { provide: DialogService, useClass: MockDialogService },
                ],
            });

            fixture = TestBed.createComponent(ConversationDetailDialogComponent);
            component = fixture.componentInstance;
            initializeDialog(component, fixture, { course, activeConversation, selectedTab: ConversationDetailTabs.INFO });
            fixture.changeDetectorRef.detectChanges();
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.isInitialized).toBe(true);
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
            expect(component.changesWerePerformed).toBe(false);

            membersComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBe(true);
        });

        it('should react correctly to events from info tab', () => {
            component.selectedTab = ConversationDetailTabs.INFO;
            fixture.changeDetectorRef.detectChanges();

            const infoComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-info'));
            expect(infoComponentDebug).toBeTruthy();

            const infoComponent = infoComponentDebug.componentInstance;
            expect(infoComponent).toBeTruthy();
            expect(component.changesWerePerformed).toBe(false);

            infoComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBe(true);
        });

        it('should react correctly to events from settings tab', () => {
            if (!component.isOneToOneChat) {
                component.selectedTab = ConversationDetailTabs.SETTINGS;
                fixture.changeDetectorRef.detectChanges();

                const settingsComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-settings'));
                expect(settingsComponentDebug).toBeTruthy();

                const settingsComponent = settingsComponentDebug.componentInstance;
                expect(settingsComponent).toBeTruthy();

                const dialogRef = TestBed.inject(DynamicDialogRef);
                const closeSpy = vi.spyOn(dialogRef, 'close');

                settingsComponent.channelArchivalChange.emit();
                expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);

                closeSpy.mockClear();
                settingsComponent.channelDeleted.emit();
                expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);

                closeSpy.mockClear();
                settingsComponent.conversationLeave.emit();
                expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);
            }
        });

        it('should mark changes and close the dialog on privacy/archival/channelDeleted/leave events', () => {
            const dialogRef = TestBed.inject(DynamicDialogRef);
            const closeSpy = vi.spyOn(dialogRef, 'close');

            expect(component.changesWerePerformed).toBe(false);

            component.onPrivacyChange();
            expect(component.changesWerePerformed).toBe(true);
            expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onArchivalChange();
            expect(component.changesWerePerformed).toBe(true);
            expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onChannelDeleted();
            expect(component.changesWerePerformed).toBe(true);
            expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);

            closeSpy.mockClear();
            component.changesWerePerformed = false;

            component.onConversationLeave();
            expect(component.changesWerePerformed).toBe(true);
            expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);
        });

        it('should emit userNameClicked event when onUserNameClicked is called', () => {
            const testUserId = 42;
            const spy = vi.spyOn(component.userNameClicked, 'emit');
            component.onUserNameClicked(testUserId);
            expect(spy).toHaveBeenCalledWith(testUserId);
        });

        it('should invoke the username click callback from dialog data without replacing the component method', () => {
            const testUserId = 42;
            const callback = vi.fn();
            component.dialogConfig!.data.onUserNameClicked = callback;
            const emitSpy = vi.spyOn(component.userNameClicked, 'emit');

            component.onUserNameClicked(testUserId);

            expect(emitSpy).toHaveBeenCalledWith(testUserId);
            expect(callback).toHaveBeenCalledWith(testUserId);
        });
    });
});
