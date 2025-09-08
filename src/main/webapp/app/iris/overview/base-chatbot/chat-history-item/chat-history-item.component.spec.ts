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

describe('ChatHistoryItemComponent', () => {
    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent],
            providers: [DatePipe],
            declarations: [MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(ChatHistoryItemComponent);
        component = fixture.componentInstance;
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

    it('should display formatted creation date', () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.detectChanges();

        const datePipe = new DatePipe('en-US');
        const expectedDate = datePipe.transform(component.session()!.creationDate, 'dd.MM.yy HH:mm');
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('.chat-history-text');
        expect(historyItemLabel.textContent).toContain(expectedDate);
    });

    it('should display session title when present, instead of the creation date', () => {
        fixture.componentRef.setInput('session', getTitledSessionMock());
        fixture.detectChanges();

        const expectedTitle = 'New chat';
        const historyItemLabel: HTMLElement = fixture.nativeElement.querySelector('.chat-history-text');
        expect(historyItemLabel.textContent).toContain(expectedTitle);
    });

    it('should emit sessionClicked when item is clicked', () => {
        fixture.componentRef.setInput('session', getSessionMock());
        jest.spyOn(component.sessionClicked, 'emit');
        fixture.detectChanges();

        const itemDiv = fixture.debugElement.query(By.css('.chat-history-item'));
        itemDiv.triggerEventHandler('click', null);

        expect(component.sessionClicked.emit).toHaveBeenCalledWith(component.session()!);
    });

    it('should add "chat-history-item-selected" class when active is true', () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', true);
        fixture.detectChanges();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).toContain('chat-history-item-selected');
    });

    it('should not add "chat-history-item-selected" class when active is false', () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.componentRef.setInput('active', false);
        fixture.detectChanges();

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

    it('should not render an icon and entity name for course session', () => {
        const session: IrisSessionDTO = {
            id: 3,
            title: 'New chat',
            creationDate: new Date(),
            chatMode: ChatServiceMode.COURSE,
            entityId: 123,
            entityName: 'Course 1',
        };

        fixture.componentRef.setInput('session', session);
        fixture.detectChanges();

        const iconDebugEl = fixture.debugElement.query(By.directive(FaIconComponent));

        expect(iconDebugEl).toBeNull();
        expect(fixture.debugElement.query(By.css('.related-entity-name'))).toBeFalsy();
    });
});
