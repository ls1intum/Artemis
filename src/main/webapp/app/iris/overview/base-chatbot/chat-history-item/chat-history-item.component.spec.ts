import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatHistoryItemComponent } from './chat-history-item.component';
import { By } from '@angular/platform-browser';
import { DatePipe } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ChatHistoryItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [DatePipe, { provide: TranslateService, useClass: MockTranslateService }],
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
});
