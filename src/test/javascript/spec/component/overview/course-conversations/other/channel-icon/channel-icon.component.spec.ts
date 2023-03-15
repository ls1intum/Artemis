import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';

import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';

describe('ChannelIconComponent', () => {
    let component: ChannelIconComponent;
    let fixture: ComponentFixture<ChannelIconComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelIconComponent, MockComponent(FaIconComponent)] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelIconComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
