import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationInfoComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-info/conversation-info.component';

describe('ConversationInfoComponent', () => {
    let component: ConversationInfoComponent;
    let fixture: ComponentFixture<ConversationInfoComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationInfoComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
