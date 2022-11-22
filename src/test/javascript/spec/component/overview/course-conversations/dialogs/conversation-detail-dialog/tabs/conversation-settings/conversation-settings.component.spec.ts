import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationSettingsComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-settings/conversation-settings.component';

describe('ConversationSettingsComponent', () => {
    let component: ConversationSettingsComponent;
    let fixture: ComponentFixture<ConversationSettingsComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ConversationSettingsComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
