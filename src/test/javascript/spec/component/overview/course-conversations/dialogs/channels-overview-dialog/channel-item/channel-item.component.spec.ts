import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelItemComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channel-item/channel-item.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { generateExampleChannelDTO } from '../../../helpers/conversationExampleModels';

describe('ChannelItemComponent', () => {
    let component: ChannelItemComponent;
    let fixture: ComponentFixture<ChannelItemComponent>;
    const canJoinChannel = jest.fn();
    const canLeaveConversation = jest.fn();
    const channel = generateExampleChannelDTO({ id: 1 });

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelItemComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ChannelIconComponent)] }).compileComponents();
    }));

    beforeEach(() => {
        canJoinChannel.mockReturnValue(true);
        canLeaveConversation.mockReturnValue(true);
        fixture = TestBed.createComponent(ChannelItemComponent);
        component = fixture.componentInstance;
        component.canJoinChannel = canJoinChannel;
        component.canLeaveConversation = canLeaveConversation;
        component.channel = channel;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show buttons only if user has the required permissions', () => {
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        canJoinChannel.mockReturnValue(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        canLeaveConversation.mockReturnValue(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeFalsy();

        // change dto to one where not is member
        component.channel = generateExampleChannelDTO({ id: 2, isMember: false });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeFalsy();
    });

    it('should emit channel action when the user clicks on the interaction buttons', () => {
        const emitAction = jest.spyOn(component.channelAction, 'emit');
        fixture.nativeElement.querySelector('#view' + channel.id).click();
        expect(emitAction).toHaveBeenCalledWith({ action: 'view', channel });

        emitAction.mockClear();
        fixture.nativeElement.querySelector('#register' + channel.id).click();
        expect(emitAction).toHaveBeenCalledWith({ action: 'register', channel });

        emitAction.mockClear();
        fixture.nativeElement.querySelector('#deregister' + channel.id).click();
        expect(emitAction).toHaveBeenCalledWith({ action: 'deregister', channel });
    });
});
