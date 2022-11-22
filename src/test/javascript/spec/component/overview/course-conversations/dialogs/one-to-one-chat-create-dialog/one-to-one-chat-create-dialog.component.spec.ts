import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';

describe('OneToOneChatCreateDialogComponent', () => {
    let component: OneToOneChatCreateDialogComponent;
    let fixture: ComponentFixture<OneToOneChatCreateDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [OneToOneChatCreateDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(OneToOneChatCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
