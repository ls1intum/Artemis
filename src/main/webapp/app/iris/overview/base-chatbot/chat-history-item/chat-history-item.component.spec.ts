import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ChatHistoryItemComponent } from './chat-history-item.component';
import { By } from '@angular/platform-browser';
import { TestKey } from '@angular/cdk/testing';
import { MenuHarness } from '@angular/aria/menu/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
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
    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent, MockPipe(ArtemisTranslatePipe, (key) => key ?? '')],
            providers: [DatePipe, { provide: TranslateService, useClass: MockTranslateService }, provideRouter([])],
        });

        fixture = TestBed.createComponent(ChatHistoryItemComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        fixture.destroy();
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
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('button[data-button-intent="selection"]');
        expect(historyItemLabel.textContent).toContain(expectedDate);
    });

    it('should display session title when present, instead of the creation date', async () => {
        fixture.componentRef.setInput('session', getTitledSessionMock());
        await fixture.whenStable();

        const expectedTitle = 'New chat';
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('button[data-button-intent="selection"]');
        expect(historyItemLabel.textContent).toContain(expectedTitle);
    });

    it('should emit sessionClicked when item is clicked', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        vi.spyOn(component.sessionClicked, 'emit');
        await fixture.whenStable();

        const sessionButton = fixture.debugElement.query(By.css('button[data-button-intent="selection"]')).nativeElement as HTMLButtonElement;
        sessionButton.click();

        expect(component.sessionClicked.emit).toHaveBeenCalledWith(component.session()!);
    });

    it('should add "chat-history-item--selected" class when active is true', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', true);
        await fixture.whenStable();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).toContain('chat-history-item--selected');
    });

    it('should not add "chat-history-item--selected" class when active is false', async () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', false);
        await fixture.whenStable();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).not.toContain('chat-history-item--selected');
    });

    function testSessionRendering(session: IrisSessionDTO, expectedIcon: IconProp, expectedTooltipKey: string, expectedEntityRoute: string) {
        fixture.componentRef.setInput('session', session);
        fixture.detectChanges();
        const iconDebugEl = fixture.debugElement.query(By.css('.chat-history-item__entity-link fa-icon'));
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
            mode: ChatServiceMode.LECTURE,
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
            mode: ChatServiceMode.PROGRAMMING_EXERCISE,
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
            mode: ChatServiceMode.TEXT_EXERCISE,
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
            mode: ChatServiceMode.COURSE,
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
            mode: ChatServiceMode.COURSE,
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
            mode: ChatServiceMode.COURSE,
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
            mode: ChatServiceMode.COURSE,
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
            mode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const menuTrigger = fixture.debugElement.query(By.css('.chat-history-item__menu-trigger'));
        expect(menuTrigger).toBeTruthy();
    });

    it('should emit deleteSession when delete is clicked via onDeleteClick', async () => {
        const session: IrisSessionDTO = {
            id: 11,
            title: 'Chat to delete',
            creationDate: new Date(),
            mode: ChatServiceMode.COURSE,
            entityId: 1,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.deleteSession, 'emit');
        component.onDeleteClick();

        expect(component.deleteSession.emit).toHaveBeenCalledWith(session);
    });

    const menuSession: IrisSessionDTO = {
        id: 12,
        title: 'Chat with menu',
        creationDate: new Date(),
        mode: ChatServiceMode.COURSE,
        entityId: 1,
        entityName: 'Course 1',
    };

    const openMenu = async () => {
        fixture.componentRef.setInput('session', menuSession);
        await fixture.whenStable();
        fixture.debugElement.query(By.css('.chat-history-item__menu-trigger')).nativeElement.click();
        await fixture.whenStable();
        return TestbedHarnessEnvironment.documentRootLoader(fixture).getHarness(MenuHarness);
    };

    it('should offer the delete action in the session menu', async () => {
        const menu = await openMenu();
        const entries = await menu.getItems();

        expect(entries).toHaveLength(1);
        expect(await entries[0].getText()).toBe('artemisApp.iris.chatHistory.deleteSession');
    });

    it('should emit deleteSession, and not sessionClicked, when the delete action is chosen', async () => {
        vi.spyOn(component.deleteSession, 'emit');
        vi.spyOn(component.sessionClicked, 'emit');

        const menu = await openMenu();
        const [deleteItem] = await menu.getItems({ text: 'artemisApp.iris.chatHistory.deleteSession' });
        await deleteItem.click();
        await fixture.whenStable();

        expect(component.deleteSession.emit).toHaveBeenCalledWith(menuSession);
        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should not choose the session when the menu is opened with a click', async () => {
        vi.spyOn(component.sessionClicked, 'emit');

        const menu = await openMenu();

        expect(await menu.isOpen()).toBe(true);
        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it.each(['Enter', ' '])('should not choose the session when the menu is opened with %j', async (key) => {
        fixture.componentRef.setInput('session', menuSession);
        await fixture.whenStable();
        vi.spyOn(component.sessionClicked, 'emit');

        const trigger = fixture.debugElement.query(By.css('.chat-history-item__menu-trigger')).nativeElement as HTMLButtonElement;
        trigger.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        await fixture.whenStable();

        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
        const menu = await TestbedHarnessEnvironment.documentRootLoader(fixture).getHarness(MenuHarness);
        expect(await menu.isOpen()).toBe(true);
        const [deleteItem] = await menu.getItems();
        expect(await deleteItem.isFocused()).toBe(true);
    });

    it('shows the menu tooltip when the native trigger receives keyboard focus', async () => {
        fixture.componentRef.setInput('session', menuSession);
        await fixture.whenStable();
        const trigger = fixture.debugElement.query(By.css('.chat-history-item__menu-trigger')).nativeElement as HTMLButtonElement;
        trigger.focus();
        await vi.waitFor(() => {
            fixture.detectChanges();
            const tooltip = document.querySelector('[role="tooltip"]');
            expect(tooltip?.textContent).toContain('artemisApp.iris.chatHistory.sessionMenuAriaLabel');
        });
        expect(document.activeElement).toBe(trigger);
    });

    it('should not render related-entity-icon for course session', async () => {
        const session: IrisSessionDTO = {
            id: 3,
            title: 'Course chat',
            creationDate: new Date(),
            mode: ChatServiceMode.COURSE,
            entityId: 123,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const iconDebugEl = fixture.debugElement.query(By.css('.chat-history-item__entity-link'));
        expect(iconDebugEl).toBeNull();
    });

    it('keeps selection, menu and navigation as sibling native controls', async () => {
        fixture.componentRef.setInput('session', { ...menuSession, mode: ChatServiceMode.PROGRAMMING_EXERCISE });
        await fixture.whenStable();

        const row = fixture.nativeElement.querySelector('.chat-history-item') as HTMLElement;
        expect(row.getAttribute('role')).toBeNull();
        expect(row.hasAttribute('tabindex')).toBe(false);
        const controls = Array.from(row.children).filter((child) => child.matches('button, a'));
        expect(controls.map((control) => control.tagName)).toEqual(['BUTTON', 'BUTTON', 'A']);
        expect(controls[0].textContent).toContain(menuSession.title);
        expect(controls[1].getAttribute('aria-label')).toBe('artemisApp.iris.chatHistory.sessionMenuAriaLabel');
        expect(controls[2].getAttribute('href')).toContain('/exercises/1');
    });

    it('returns focus to the menu trigger on Escape without selecting the session', async () => {
        vi.spyOn(component.sessionClicked, 'emit');
        const menu = await openMenu();
        await (await menu.host()).sendKeys(TestKey.ESCAPE);
        await fixture.whenStable();

        const trigger = fixture.debugElement.query(By.css('.chat-history-item__menu-trigger')).nativeElement;
        expect(document.activeElement).toBe(trigger);
        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should not emit sessionClicked when Enter is pressed on entity icon link', async () => {
        const session: IrisSessionDTO = {
            id: 21,
            title: 'Exercise chat',
            creationDate: new Date(),
            mode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.sessionClicked, 'emit');

        const entityIcon = fixture.debugElement.query(By.css('.chat-history-item__entity-link'));
        entityIcon.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should not emit sessionClicked when Space is pressed on entity icon link', async () => {
        const session: IrisSessionDTO = {
            id: 22,
            title: 'Exercise chat',
            creationDate: new Date(),
            mode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 99,
            entityName: 'Exercise 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        vi.spyOn(component.sessionClicked, 'emit');

        const entityIcon = fixture.debugElement.query(By.css('.chat-history-item__entity-link'));
        entityIcon.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

        expect(component.sessionClicked.emit).not.toHaveBeenCalled();
    });

    it('should recompute tooltipText when locale changes', async () => {
        const session: IrisSessionDTO = {
            id: 30,
            title: 'Locale test chat',
            creationDate: new Date(),
            mode: ChatServiceMode.PROGRAMMING_EXERCISE,
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
