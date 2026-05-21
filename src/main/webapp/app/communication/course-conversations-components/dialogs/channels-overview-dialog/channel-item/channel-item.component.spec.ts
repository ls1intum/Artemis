import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { generateExampleChannelDTO } from 'test/helpers/sample/conversationExampleModels';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ChannelItemComponent } from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channel-item/channel-item.component';

describe('ChannelItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChannelItemComponent;
    let fixture: ComponentFixture<ChannelItemComponent>;
    const canJoinChannel = vi.fn();
    const canLeaveConversation = vi.fn();
    const channel = generateExampleChannelDTO({ id: 1 } as ChannelDTO);

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ChannelItemComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ChannelIconComponent), MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
    });

    beforeEach(() => {
        canJoinChannel.mockReturnValue(true);
        canLeaveConversation.mockReturnValue(true);
        fixture = TestBed.createComponent(ChannelItemComponent);
        component = fixture.componentInstance;
        component.canJoinChannel = canJoinChannel;
        component.canLeaveConversation = canLeaveConversation;
        fixture.componentRef.setInput('channel', channel);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show buttons only if user has the required permissions', () => {
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        canJoinChannel.mockReturnValue(false);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeTruthy();

        canLeaveConversation.mockReturnValue(false);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeFalsy();

        // change dto to one where not is member
        fixture.componentRef.setInput('channel', generateExampleChannelDTO({ id: 2, isMember: false } as ChannelDTO));
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('#view' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#register' + channel.id)).toBeFalsy();
        expect(fixture.nativeElement.querySelector('#deregister' + channel.id)).toBeFalsy();
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
