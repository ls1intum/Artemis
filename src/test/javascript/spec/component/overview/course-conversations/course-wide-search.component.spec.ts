import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Directive, EventEmitter, Input, Output } from '@angular/core';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { BehaviorSubject } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { metisExamChannelDTO, metisExerciseChannelDTO, metisGeneralChannelDTO, metisLectureChannelDTO } from '../../../helpers/sample/metis-sample-data';

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
describe('CourseWideSearchComponent', () => {
    let component: CourseWideSearchComponent;
    let fixture: ComponentFixture<CourseWideSearchComponent>;

    let metisService: MetisService;
    let metisConversationService: MetisConversationService;
    let examplePost: Post;
    const course = { id: 1 } as Course;
    const conversationDtoArray = [metisGeneralChannelDTO, metisExerciseChannelDTO, metisLectureChannelDTO, metisExamChannelDTO];

    const courseWideSearchConfig = new CourseWideSearchConfig();
    courseWideSearchConfig.searchTerm = '';
    courseWideSearchConfig.filterToUnresolved = false;
    courseWideSearchConfig.filterToOwn = false;
    courseWideSearchConfig.filterToAnsweredOrReacted = false;
    courseWideSearchConfig.sortingOrder = SortDirection.ASCENDING;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [
                CourseWideSearchComponent,
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
        examplePost = { id: 1, content: 'test' } as Post;

        metisService = TestBed.inject(MetisService);
        metisConversationService = TestBed.inject(MetisConversationService);
        Object.defineProperty(metisConversationService, 'isServiceSetup$', {
            get: () => new BehaviorSubject(true).asObservable(),
        });
        Object.defineProperty(metisConversationService, 'conversationsOfUser$', {
            get: () => new BehaviorSubject(conversationDtoArray).asObservable(),
        });
        Object.defineProperty(metisService, 'totalNumberOfPosts', { get: () => new BehaviorSubject(1).asObservable() });
        Object.defineProperty(metisService, 'createEmptyPostForContext', { value: () => new Post() });
        Object.defineProperty(metisConversationService, 'course', { get: () => course });
        Object.defineProperty(metisService, 'posts', { get: () => new BehaviorSubject([examplePost]).asObservable() });

        fixture = TestBed.createComponent(CourseWideSearchComponent);
        component = fixture.componentInstance;
        component.course = course;
        component.courseWideSearchConfig = courseWideSearchConfig;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        component.handleScrollOnNewMessage();
    });

    it('should set initial values correctly', fakeAsync(() => {
        component.ngOnInit();
        expect(component.course).toBe(course);
        expect(component.posts).toStrictEqual([examplePost]);
        expect(component.courseWideSearchConfig).toBe(courseWideSearchConfig);
    }));

    it('should initialize currentPostContextFilter correctly', fakeAsync(() => {
        component.ngOnInit();
        const conversationsOfUser = conversationDtoArray.map((conversation) => conversation.id);
        expect(component.currentPostContextFilter).toEqual({
            courseId: course.id,
            courseWideChannelIds: conversationsOfUser,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            page: 0,
            pageSize: 50,
            pagingEnabled: true,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.ASCENDING,
        });
    }));

    it('should update currentPostContextFilter correctly', fakeAsync(() => {
        component.ngOnInit();
        const conversationsOfUser = conversationDtoArray.map((conversation) => conversation.id);

        courseWideSearchConfig.searchTerm = 'test';
        courseWideSearchConfig.filterToOwn = true;
        component.onSearch();
        tick();
        fixture.detectChanges();

        expect(component.currentPostContextFilter).toEqual({
            courseId: course.id,
            courseWideChannelIds: conversationsOfUser,
            searchText: courseWideSearchConfig.searchTerm,
            filterToUnresolved: courseWideSearchConfig.filterToUnresolved,
            filterToOwn: courseWideSearchConfig.filterToOwn,
            filterToAnsweredOrReacted: courseWideSearchConfig.filterToAnsweredOrReacted,
            page: 0,
            pageSize: 50,
            pagingEnabled: true,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.ASCENDING,
        });
    }));

    it('should fetch posts on next page fetch', fakeAsync(() => {
        const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
        component.courseWideSearchConfig = courseWideSearchConfig;
        component.totalNumberOfPosts = 10;
        component.fetchNextPage();
        expect(getFilteredPostSpy).toHaveBeenCalledOnce();
    }));
});
