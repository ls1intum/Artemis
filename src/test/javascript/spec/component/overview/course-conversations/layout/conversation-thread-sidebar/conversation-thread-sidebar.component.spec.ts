import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { MessageReplyInlineInputComponent } from 'app/shared/metis/message/message-reply-inline-input/message-reply-inline-input.component';
import { Post } from 'app/entities/metis/post.model';
import { post } from '../../../../../helpers/sample/metis-sample-data';
import { NgbTooltipMocksModule } from '../../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('ConversationThreadSidebarComponent', () => {
    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                ConversationThreadSidebarComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
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
});
