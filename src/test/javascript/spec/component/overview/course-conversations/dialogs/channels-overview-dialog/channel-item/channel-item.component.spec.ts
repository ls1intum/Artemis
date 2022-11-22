import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelItemComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channel-item/channel-item.component';

describe('ChannelItemComponent', () => {
    let component: ChannelItemComponent;
    let fixture: ComponentFixture<ChannelItemComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelItemComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelItemComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
