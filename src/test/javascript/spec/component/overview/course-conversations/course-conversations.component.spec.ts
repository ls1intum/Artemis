import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from './helpers/conversationExampleModels';
import { AlertService } from 'app/core/util/alert.service';
import { mockedActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course } from 'app/entities/course.model';
import { EMPTY } from 'rxjs';
import { BehaviorSubject } from 'rxjs';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { Post } from 'app/entities/metis/post.model';
const examples: (ConversationDto | undefined)[] = [undefined, generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('CourseConversationComponent with ' + (activeConversation?.type || 'no active conversation'), () => {
        let component: CourseConversationsComponent;
        let fixture: ComponentFixture<CourseConversationsComponent>;
        let metisConversationService: MetisConversationService;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            metisConversationService = {} as MetisConversationService;
            TestBed.configureTestingModule({
                declarations: [
                    CourseConversationsComponent,
                    LoadingIndicatorContainerStubComponent,
                    MockComponent(ConversationSelectionSidebarComponent),
                    MockComponent(ConversationHeaderComponent),
                    MockComponent(ConversationMessagesComponent),
                    MockComponent(ConversationThreadSidebarComponent),
                ],
                providers: [MockProvider(AlertService), MockProvider(MetisConversationService), mockedActivatedRoute({}, {}, {}, {}, { courseId: 1 })],
            });

            fixture = TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [
                        {
                            provide: MetisConversationService,
                            useValue: metisConversationService,
                        },
                    ],
                },
            }).createComponent(CourseConversationsComponent);

            Object.defineProperty(metisConversationService, 'course', { get: () => course });
            Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });
            Object.defineProperty(metisConversationService, 'forceRefresh', { value: () => EMPTY });
            Object.defineProperty(metisConversationService, 'setUpConversationService', { value: () => EMPTY });
            Object.defineProperty(metisConversationService, 'conversationsOfUser$', {
                get: () => new BehaviorSubject([new GroupChatDto()]).asObservable(),
            });
            Object.defineProperty(metisConversationService, 'isLoading$', {
                get: () => new BehaviorSubject(false).asObservable(),
            });
            component = fixture.componentInstance;
        }));

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should have service set up', () => {
            fixture.detectChanges();
            expect(component.isServiceSetUp).toBeTrue();
            expect(component.isLoading).toBeFalse();
            expect(component.conversationsOfUser).toHaveLength(1);
            expect(component.activeConversation).toEqual(activeConversation);
        });

        it('should set thread post', () => {
            fixture.detectChanges();
            const post = { id: 1 } as Post;
            component.setPostInThread(post);
            expect(component.postInThread).toEqual(post);
            expect(component.showPostThread).toBeTrue();
        });
    });
});
