import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatHistoryItemComponent } from './chat-history-item.component';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { By } from '@angular/platform-browser';
import { DatePipe } from '@angular/common';

describe('ChatHistoryItemComponent', () => {
    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent], // standalone component
            providers: [DatePipe],
        }).compileComponents();

        fixture = TestBed.createComponent(ChatHistoryItemComponent);
        component = fixture.componentInstance;
    });

    function getSessionMock(): IrisSession {
        return {
            id: 1,
            creationDate: new Date('2023-10-15T14:23:00'),
            // add any other required IrisSession fields here
        } as unknown as IrisSession;
    }

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display formatted creation date', () => {
        component.session = getSessionMock();
        fixture.detectChanges();

        const datePipe = new DatePipe('en-US');
        const expectedDate = datePipe.transform(component.session.creationDate, 'dd.MM.yy HH:mm');
        const pElem: HTMLElement = fixture.nativeElement.querySelector('p');

        expect(pElem.textContent).toContain(expectedDate);
    });

    it('should emit sessionClicked when item is clicked', () => {
        component.session = getSessionMock();
        jest.spyOn(component.sessionClicked, 'emit');
        fixture.detectChanges();

        const itemDiv = fixture.debugElement.query(By.css('.chat-history-item'));
        itemDiv.triggerEventHandler('click', null);

        expect(component.sessionClicked.emit).toHaveBeenCalledWith(component.session);
    });

    it('should add "chat-history-item-selected" class when active is true', () => {
        component.session = getSessionMock();
        component.active = true;
        fixture.detectChanges();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).toContain('chat-history-item-selected');
    });

    it('should not add "chat-history-item-selected" class when active is false', () => {
        component.session = getSessionMock();
        component.active = false;
        fixture.detectChanges();

        const itemDiv: HTMLElement = fixture.nativeElement.querySelector('.chat-history-item');
        expect(itemDiv.classList).not.toContain('chat-history-item-selected');
    });
});
