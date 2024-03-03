import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { BehaviorSubject } from 'rxjs';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { Directive, EventEmitter, Input, Output } from '@angular/core';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

const examples: ConversationDTO[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({}),
    generateExampleChannelDTO({ isAnnouncementChannel: true }),
];

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[infiniteScroll], [infinite-scroll], [data-infinite-scroll]',
})
class InfiniteScrollStubDirective {
    @Output() scrolled = new EventEmitter<void>();
    @Output() scrolledUp = new EventEmitter<void>();

    @Input() infiniteScrollDistance = 2;
    @Input() infiniteScrollUpDistance = 1.5;
    @Input() infiniteScrollThrottle = 150;
    @Input() infiniteScrollDisabled = false;
    @Input() infiniteScrollContainer: any = null;
    @Input() scrollWindow = true;
    @Input() immediateCheck = false;
    @Input() horizontal = false;
    @Input() alwaysCallback = false;
    @Input() fromRoot = false;
}
examples.forEach((activeConversation) => {
    describe('ConversationMessagesComponent with ' + (getAsChannelDTO(activeConversation)?.isAnnouncementChannel ? 'announcement ' : '') + activeConversation.type, () => {
        let component: ConversationMessagesComponent;
        let fixture: ComponentFixture<ConversationMessagesComponent>;
        let metisService: MetisService;
        let metisConversationService: MetisConversationService;
        let examplePost: Post;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule],
                declarations: [
                    ConversationMessagesComponent,
                    InfiniteScrollStubDirective,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ButtonComponent),
                    MockComponent(FaIconComponent),
                    MockComponent(PostingThreadComponent),
                    MockComponent(MessageInlineInputComponent),
                    MockComponent(PostCreateEditModalComponent),
                ],
                providers: [MockProvider(MetisConversationService), MockProvider(MetisService), MockProvider(NgbModal)],
            }).compileComponents();
        }));

        beforeEach(() => {
            examplePost = { id: 1, content: 'loremIpsum' } as Post;

            metisService = TestBed.inject(MetisService);
            metisConversationService = TestBed.inject(MetisConversationService);
            Object.defineProperty(metisService, 'posts', { get: () => new BehaviorSubject([examplePost]).asObservable() });
            Object.defineProperty(metisService, 'totalNumberOfPosts', { get: () => new BehaviorSubject(1).asObservable() });
            Object.defineProperty(metisService, 'createEmptyPostForContext', { value: () => new Post() });
            Object.defineProperty(metisConversationService, 'course', { get: () => course });
            Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });

            fixture = TestBed.createComponent(ConversationMessagesComponent);
            component = fixture.componentInstance;
            component.course = course;
            fixture.detectChanges();
        });

        afterEach(() => {
            jest.clearAllMocks();
        });

        it('should create', fakeAsync(() => {
            expect(component).toBeTruthy();
            component.handleScrollOnNewMessage();
        }));

        it('should set initial values correctly', fakeAsync(() => {
            component.course = course;
            component._activeConversation = activeConversation;
            component.posts = [examplePost];
        }));

        it('should fetch posts on search input and clear search again on clear button press', fakeAsync(() => {
            const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
            const inputField = fixture.debugElement.query(By.css('#searchInput'));
            inputField.nativeElement.value = 'test';
            inputField.nativeElement.dispatchEvent(new Event('input'));
            tick(301);
            expect(component.searchText).toBe('test');
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
            fixture.detectChanges();

            getFilteredPostSpy.mockClear();
            const clearButton = fixture.debugElement.query(By.css('#clearSearchButton'));
            clearButton.nativeElement.click();
            tick(301);
            expect(component.searchText).toBe('');
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
        }));

        it('should fetch posts on next page fetch', fakeAsync(() => {
            const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
            component.searchText = 'loremIpsum';
            component.totalNumberOfPosts = 10;
            component.fetchNextPage();
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
        }));

        it('should create empty post with the correct conversation type', fakeAsync(() => {
            const createEmptyPostForContextSpy = jest.spyOn(metisService, 'createEmptyPostForContext').mockReturnValue(new Post());
            component.createEmptyPost();
            expect(createEmptyPostForContextSpy).toHaveBeenCalledOnce();
            const conversation = createEmptyPostForContextSpy.mock.calls[0][0];
            expect(conversation!.type).toEqual(activeConversation.type);
            expect(conversation!.id).toEqual(activeConversation.id);
        }));

        if (getAsChannelDTO(activeConversation)?.isAnnouncementChannel) {
            it('should display the "new announcement" button when the conversation is an announcement channel', fakeAsync(() => {
                const announcementButton = fixture.debugElement.query(By.css('.btn.btn-md.btn-primary'));
                expect(announcementButton).toBeTruthy(); // Check if the button is present

                const modal = fixture.debugElement.query(By.directive(PostCreateEditModalComponent));
                expect(modal).toBeTruthy(); // Check if the modal is present
            }));
        } else {
            it('should display the inline input when the conversation is not an announcement channel', fakeAsync(() => {
                const inlineInput = fixture.debugElement.query(By.directive(MessageInlineInputComponent));
                expect(inlineInput).toBeTruthy(); // Check if the inline input component is present
            }));
        }
    });
});
