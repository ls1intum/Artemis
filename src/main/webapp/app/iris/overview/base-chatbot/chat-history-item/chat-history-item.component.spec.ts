import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatHistoryItemComponent } from './chat-history-item.component';
import { By } from '@angular/platform-browser';
import { DatePipe } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';

describe('ChatHistoryItemComponent', () => {
    let component: ChatHistoryItemComponent;
    let fixture: ComponentFixture<ChatHistoryItemComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChatHistoryItemComponent],
            providers: [DatePipe],
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

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display formatted creation date', () => {
        fixture.componentRef.setInput('session', getSessionMock());
        fixture.detectChanges();

        const datePipe = new DatePipe('en-US');
        const expectedDate = datePipe.transform(component.session()!.creationDate, 'dd.MM.yy HH:mm');
        const pElem: HTMLElement = fixture.nativeElement.querySelector('p');

        expect(pElem.textContent).toContain(expectedDate);
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
});
