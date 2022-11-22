import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';

describe('ChannelsOverviewDialogComponent', () => {
    let component: ChannelsOverviewDialogComponent;
    let fixture: ComponentFixture<ChannelsOverviewDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelsOverviewDialogComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelsOverviewDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
