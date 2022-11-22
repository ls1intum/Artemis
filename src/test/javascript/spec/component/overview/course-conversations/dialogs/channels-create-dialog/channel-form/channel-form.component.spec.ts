import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelFormComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';

describe('ChannelFormComponent', () => {
    let component: ChannelFormComponent;
    let fixture: ComponentFixture<ChannelFormComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelFormComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelFormComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it.todo('should create');
});
