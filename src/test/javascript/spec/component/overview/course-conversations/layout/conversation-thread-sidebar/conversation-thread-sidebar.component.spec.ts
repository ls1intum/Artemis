import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { PostComponent } from 'app/communication/post/post.component';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { Post } from 'app/entities/metis/post.model';
import { post } from '../../../../../helpers/sample/metis-sample-data';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { runInInjectionContext, signal } from '@angular/core';

describe('ConversationThreadSidebarComponent', () => {
    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [
                ConversationThreadSidebarComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
                MockDirective(TranslateDirective),
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationThreadSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should create empty default answer post', () => {
        const newPost = new Post();
        post.id = 1;
        component.activePost = newPost;
        expect(component.createdAnswerPost).toBeDefined();
        expect(component.createdAnswerPost.content).toBe('');
        expect(component.createdAnswerPost.post).toEqual(newPost);
    });

    it.each([true, false])('should determine channel moderation rights based on active conversation', (hasModerationRights: boolean) => {
        const conversation = new ChannelDTO();
        conversation.hasChannelModerationRights = hasModerationRights;
        component.activeConversation = conversation;
        expect(component.hasChannelModerationRights).toBe(hasModerationRights);
    });

    it('should set min and max width for the resizable thread section', () => {
        const expandedThreadElement = fixture.debugElement.nativeElement.querySelector('.expanded-thread');
        const minWidth = window.innerWidth * 0.3;
        const maxWidth = window.innerWidth;

        expandedThreadElement.style.width = `${minWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeGreaterThanOrEqual(minWidth);

        expandedThreadElement.style.width = `${maxWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeLessThanOrEqual(maxWidth);
    });

    it('should toggle isExpanded and call close() on expandTooltip signal when toggleExpand() is called', () => {
        const closeMock = jest.fn();

        runInInjectionContext(fixture.debugElement.injector, () => {
            component.expandTooltip = signal<NgbTooltip | undefined>({ close: closeMock } as unknown as NgbTooltip);
        });

        expect(component.isExpanded).toBe(false);

        component.toggleExpand();
        expect(component.isExpanded).toBe(true);
        expect(closeMock).toHaveBeenCalledTimes(1);

        component.toggleExpand();
        expect(component.isExpanded).toBe(false);
        expect(closeMock).toHaveBeenCalledTimes(2);
    });
});
