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
import { faChalkboardUser, faKeyboard } from '@fortawesome/free-solid-svg-icons';
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

    function testSessionRendering(session: IrisSessionDTO, expectedIcon: IconProp, expectedTooltipKey: string, expectedEntityName: string) {
        fixture.componentRef.setInput('session', session);
        fixture.detectChanges();
        const iconDebugEl = fixture.debugElement.query(By.directive(FaIconComponent));
        const iconInstance = iconDebugEl.componentInstance as FaIconComponent;
        const entityNameEl = fixture.debugElement.query(By.css('.related-entity-name')).nativeElement;
        expect(iconInstance.icon()).toBe(expectedIcon);
        expect(component.tooltipKey()).toBe(expectedTooltipKey);
        expect(entityNameEl.textContent).toContain(expectedEntityName);
    }

    it('should render correct icon with correct tooltip and entity name for lecture session', () => {
        const session: IrisSessionDTO = {
            id: 1,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.LECTURE,
            entityId: 42,
            entityName: 'Lecture 1',
        };
        testSessionRendering(session, faChalkboardUser, 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture', 'Lecture 1');
    });

    it('should render correct icon with correct tooltip and entity name for programming exercise session', () => {
        const session: IrisSessionDTO = {
            id: 2,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.PROGRAMMING_EXERCISE,
            entityId: 77,
            entityName: 'Exercise 1',
        };
        testSessionRendering(session, faKeyboard, 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise', 'Exercise 1');
    });

    it('should not render an icon and entity name for course session', async () => {
        const session: IrisSessionDTO = {
            id: 3,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 123,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        await fixture.whenStable();

        const iconDebugEl = fixture.debugElement.query(By.directive(FaIconComponent));

        expect(iconDebugEl).toBeNull();
        expect(fixture.debugElement.query(By.css('.related-entity-name'))).toBeFalsy();
    });
});
