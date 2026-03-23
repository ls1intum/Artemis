import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { generateExampleChannelDTO } from 'test/helpers/sample/conversationExampleModels';
import { ChannelItemComponent } from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channel-item/channel-item.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ChannelItemComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ChannelItemComponent;
    let fixture: ComponentFixture<ChannelItemComponent>;
    const channel = generateExampleChannelDTO({ id: 1 } as ChannelDTO);

    const renderChannel = (currentChannel: ChannelDTO, canJoin = true, canLeave = true) => {
        fixture = TestBed.createComponent(ChannelItemComponent);
        component = fixture.componentInstance;
        component.canJoinChannel = vi.fn().mockReturnValue(canJoin);
        component.canLeaveConversation = vi.fn().mockReturnValue(canLeave);
        fixture.componentRef.setInput('channel', currentChannel);
        fixture.detectChanges();
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ChannelItemComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        renderChannel(channel);
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show buttons only if user has the required permissions', () => {
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        renderChannel(channel, false, true);
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        renderChannel(channel, false, false);
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeFalsy();

        // change dto to one where not is member
        const anotherChannel = generateExampleChannelDTO({ id: 2, isMember: false } as ChannelDTO);
        renderChannel(anotherChannel, false, false);
        expect(fixture.nativeElement.querySelector('#view' + anotherChannel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#register' + anotherChannel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + anotherChannel.id)).toBeFalsy();
    });

    it('should emit channel action when the user clicks on the interaction buttons', () => {
        const emitAction = vi.spyOn(component.channelAction, 'emit');
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
