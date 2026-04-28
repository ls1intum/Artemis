import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatHistoryItemComponent } from './chat-history-item.component';
import { By } from '@angular/platform-browser';
import { DatePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';
import { LangChangeEvent } from '@ngx-translate/core';

describe('ChatHistoryItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [DatePipe, { provide: TranslateService, useClass: MockTranslateService }, provideRouter([])],
        });

        fixture = TestBed.createComponent(ChatHistoryItemComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function getSessionMock(): IrisSessionDTO {
        return {
            id: 1,
            creationDate: new Date('2023-10-15T14:23:00'),
        } as unknown as IrisSessionDTO;
    }

    function getTitledSessionMock(): IrisSessionDTO {
        return {
            id: 2,
            creationDate: new Date('2023-10-15T14:23:00'),
            title: 'New chat',
        } as unknown as IrisSessionDTO;
    }

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display formatted creation date', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        await fixture.whenStable();

        const datePipe = new DatePipe('en-US');
        const expectedDate = datePipe.transform(component.session()!.creationDate, 'dd.MM.yy HH:mm');
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('.chat-history-text');
        expect(historyItemLabel.textContent).toContain(expectedDate);
    });

    it('should display session title when present, instead of the creation date', async () => {
        fixture.componentRef.setInput('session', getTitledSessionMock());
        await fixture.whenStable();

        const expectedTitle = 'New chat';
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('.chat-history-text');
        expect(historyItemLabel.textContent).toContain(expectedTitle);
    });

    it('should emit sessionClicked when item is clicked', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        vi.spyOn(component.sessionClicked, 'emit');
        await fixture.whenStable();

        const itemDiv = fixture.debugElement.query(By.css('.chat-history-item'));
        itemDiv.triggerEventHandler('click', null);

        expect(component.sessionClicked.emit).toHaveBeenCalledWith(component.session()!);
    });

    it('should add "chat-history-item-selected" class when active is true', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', true);
        await fixture.whenStable();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).toContain('chat-history-item-selected');
    });

    it('should not add "chat-history-item-selected" class when active is false', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', false);
        await fixture.whenStable();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).not.toContain('chat-history-item-selected');
    });

    function testSessionRendering(session: IrisSessionDTO, expectedIcon: IconProp, expectedTooltipKey: string, expectedEntityRoute: string) {
        fixture.componentRef.setInput('session', session);
        fixture.detectChanges();
        const iconDebugEl = fixture.debugElement.query(By.css('.related-entity-icon fa-icon'));
        const iconInstance = iconDebugEl.componentInstance as FaIconComponent;
        expect(iconInstance.icon()).toBe(expectedIcon);
        expect(component.tooltipText()).toContain(expectedTooltipKey);
        expect(component.entityRoute()).toBe(expectedEntityRoute);
    }

    it('should render correct icon with correct tooltip and entity route for lecture session', () => {
        const session: IrisSessionDTO = {
            id: 1,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.LECTURE,
            entityId: 42,
            entityName: 'Lecture 1',
        };
        testSessionRendering(session, faChalkboardUser, 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture', '../lectures/42');
    });

    it('should render correct icon with correct tooltip and entity route for programming exercise session', () => {
        const session: IrisSessionDTO = {
            id: 2,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 77,
            entityName: 'Exercise 1',
        };
        testSessionRendering(session, faKeyboard, 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise', '../exercises/77');
    });

    it('should render correct icon with correct tooltip and entity route for text exercise session', () => {
        const session: IrisSessionDTO = {
            id: 3,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.TEXT_EXERCISE,
            entityId: 55,
            entityName: 'Text Exercise 1',
        };
        testSessionRendering(session, faFont, 'artemisApp.iris.chatHistory.relatedEntityTooltip.textExercise', '../exercises/55');
    });

    it('should detect new chat session with English title', async () => {
        const session: IrisSessionDTO = {
            id: 4,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        expect(component.isNewChat()).toBe(true);
    });

    it('should detect new chat session with German title', async () => {
        const session: IrisSessionDTO = {
            id: 4,
            title: 'Neuer Chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        expect(component.isNewChat()).toBe(true);
    });

    it('should detect new chat session case-insensitively', async () => {
        const session: IrisSessionDTO = {
            id: 4,
            title: 'NEW CHAT',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        expect(component.isNewChat()).toBe(true);
    });

    it('should not detect regular session as new chat', async () => {
        const session: IrisSessionDTO = {
            id: 5,
            title: 'Some regular chat title',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        expect(component.isNewChat()).toBe(false);
    });

    it('should render menu trigger button for non-new-chat sessions', async () => {
        const session: IrisSessionDTO = {
            id: 10,
            title: 'Some chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const menuTrigger = fixture.debugElement.query(By.css('.menu-trigger'));
        expect(menuTrigger).toBeTruthy();
    });

    it('should emit deleteSession when delete is clicked via onDeleteClick', async () => {
        const session: IrisSessionDTO = {
            id: 11,
            title: 'Chat to delete',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.deleteSession, 'emit');
        component.onDeleteClick();

        expect(component.deleteSession.emit).toHaveBeenCalledWith(session);
    });

    it('should build menuItems with correct delete action on menu toggle', async () => {
        const session: IrisSessionDTO = {
            id: 12,
            title: 'Chat with menu',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        // Menu items are empty before the first toggle
        expect(component.menuItems).toHaveLength(0);

        // Simulate menu toggle (stopPropagation on the event, build items, toggle menu)
        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onMenuToggle(mockEvent);

        // After toggle, menu items should be populated with the delete action
        expect(component.menuItems).toHaveLength(1);
        expect(component.menuItems[0].styleClass).toBe('danger');
        expect(component.menuItems[0].label).toBeTruthy();
    });

    it('should emit deleteSession when menu item command is invoked', async () => {
        const session: IrisSessionDTO = {
            id: 13,
            title: 'Chat to delete via menu',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.deleteSession, 'emit');

        // Toggle to build menu items
        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onMenuToggle(mockEvent);

        // Invoke the command (simulates clicking the menu item)
        component.menuItems[0].command!({});

        expect(component.deleteSession.emit).toHaveBeenCalledWith(session);
    });

    it('should use current translation when building menu items on toggle', async () => {
        const session: IrisSessionDTO = {
            id: 14,
            title: 'Chat for language test',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const translateService = TestBed.inject(TranslateService);

        // First toggle in English
        vi.spyOn(translateService, 'instant').mockReturnValue('Delete');
        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onMenuToggle(mockEvent);
        expect(component.menuItems[0].label).toBe('Delete');

        // Simulate language change to German and toggle again
        vi.spyOn(translateService, 'instant').mockReturnValue('Löschen');
        component.onMenuToggle(mockEvent);
        expect(component.menuItems[0].label).toBe('Löschen');
    });

    it('should not render related-entity-icon for course session', async () => {
        const session: IrisSessionDTO = {
            id: 3,
            title: 'Course chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 123,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const iconDebugEl = fixture.debugElement.query(By.css('.related-entity-icon'));
        expect(iconDebugEl).toBeNull();
    });

    it('should call stopPropagation and not emit sessionClicked when entity icon is clicked', async () => {
        const session: IrisSessionDTO = {
            id: 20,
            title: 'Exercise chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.sessionClicked, 'emit');

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onEntityIconClick(mockEvent);

        expect(mockEvent.stopPropagation).toHaveBeenCalled();
        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should not emit sessionClicked when Enter is pressed on entity icon link', async () => {
        const session: IrisSessionDTO = {
            id: 21,
            title: 'Exercise chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.sessionClicked, 'emit');

        const entityIcon = fixture.debugElement.query(By.css('.related-entity-icon'));
        entityIcon.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should not emit sessionClicked when Space is pressed on entity icon link', async () => {
        const session: IrisSessionDTO = {
            id: 22,
            title: 'Exercise chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.sessionClicked, 'emit');

        const entityIcon = fixture.debugElement.query(By.css('.related-entity-icon'));
        entityIcon.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should recompute tooltipText when locale changes', async () => {
        const session: IrisSessionDTO = {
            id: 30,
            title: 'Locale test chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 50,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        const instantSpy = vi.spyOn(translateService as unknown as TranslateService, 'instant');

        // Record call count before language change
        const callsBefore = instantSpy.mock.calls.length;

        // Simulate a language change
        translateService.onLangChangeSubject.next({ lang: 'de', translations: {} } as LangChangeEvent);
        fixture.detectChanges();
        await fixture.whenStable();

        // tooltipText should have been recomputed (instant called again)
        expect(instantSpy.mock.calls.length).toBeGreaterThan(callsBefore);
    });
});
