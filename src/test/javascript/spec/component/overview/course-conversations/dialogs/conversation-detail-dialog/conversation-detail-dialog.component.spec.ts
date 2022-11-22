import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationDetailDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';

describe('ConversationDetailDialogComponent', () => {
    let component: ConversationDetailDialogComponent;
    let fixture: ComponentFixture<ConversationDetailDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationDetailDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationDetailDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
