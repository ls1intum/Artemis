import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';

describe('ChannelsCreateDialogComponent', () => {
    let component: ChannelsCreateDialogComponent;
    let fixture: ComponentFixture<ChannelsCreateDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelsCreateDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelsCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
