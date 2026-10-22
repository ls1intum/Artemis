import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Directive, input, output } from '@angular/core';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { CommunicationService } from 'app/communication/service/communication.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/course/shared/entities/course.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { BehaviorSubject } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { PostSortCriterion, SortDirection } from 'app/communication/communication.util';
import {
    communicationExamChannelDTO,
    communicationExerciseChannelDTO,
    communicationGeneralChannelDTO,
    communicationLectureChannelDTO,
} from 'test/helpers/sample/communication-sample-data';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Directive({
    selector: '[infiniteScroll], [infinite-scroll], [data-infinite-scroll]',
})
class InfiniteScrollStubDirective {
    readonly scrolled = output<void>();
    readonly scrolledUp = output<void>();

    readonly infiniteScrollDistance = input(2);
    readonly infiniteScrollUpDistance = input(1.5);
    readonly infiniteScrollThrottle = input(150);
    readonly infiniteScrollDisabled = input(false);
    readonly infiniteScrollContainer = input<any>(null);
    readonly scrollWindow = input(true);
    readonly immediateCheck = input(false);
    readonly horizontal = input(false);
    readonly alwaysCallback = input(false);
    readonly fromRoot = input(false);
}
describe('CourseWideSearchComponent', () => {
    let component: CourseWideSearchComponent;
    let fixture: ComponentFixture<CourseWideSearchComponent>;

    let communicationService: CommunicationService;
    let courseConversationsService: CourseConversationsService;
    let examplePost: Post;
    const course = { id: 1 } as Course;
    const conversationDtoArray = [communicationGeneralChannelDTO, communicationExerciseChannelDTO, communicationLectureChannelDTO, communicationExamChannelDTO];

    const courseWideSearchConfig = new CourseWideSearchConfig();
    courseWideSearchConfig.searchTerm = '';
    courseWideSearchConfig.filterToUnresolved = false;
    courseWideSearchConfig.filterToCourseWide = false;
    courseWideSearchConfig.filterToAnsweredOrReacted = false;
    courseWideSearchConfig.sortingOrder = SortDirection.DESCENDING;
    courseWideSearchConfig.selectedConversations = [];
    courseWideSearchConfig.selectedAuthors = [];

    beforeEach(() => {
        vi.useFakeTimers();
    });

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ReactiveFormsModule,
                FaIconComponent,
                CourseWideSearchComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(PostingThreadComponent),
                MockComponent(MessageInlineInputComponent),
                MockComponent(PostCreateEditModalComponent),
                MockDirective(TranslateDirective),
                InfiniteScrollStubDirective,
            ],
            providers: [
                MockProvider(CourseConversationsService),
                MockProvider(CommunicationService),
                MockProvider(DialogService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        TestBed.overrideComponent(CourseWideSearchComponent, {
            remove: { imports: [PostingThreadComponent] },
            add: { imports: [MockComponent(PostingThreadComponent)] },
        });
    });

    beforeEach(() => {
        examplePost = { id: 1, content: 'test' } as Post;

        communicationService = TestBed.inject(CommunicationService);
        courseConversationsService = TestBed.inject(CourseConversationsService);
        Object.defineProperty(courseConversationsService, 'isServiceSetup$', {
            get: () => new BehaviorSubject(true).asObservable(),
        });
        Object.defineProperty(courseConversationsService, 'conversationsOfUser$', {
            get: () => new BehaviorSubject(conversationDtoArray).asObservable(),
        });
        Object.defineProperty(communicationService, 'totalNumberOfPosts', { get: () => new BehaviorSubject(1).asObservable() });
        Object.defineProperty(communicationService, 'createEmptyPostForContext', { value: () => new Post() });
        Object.defineProperty(courseConversationsService, 'course', { get: () => course });
        Object.defineProperty(communicationService, 'posts', { get: () => new BehaviorSubject([examplePost]).asObservable() });

        fixture = TestBed.createComponent(CourseWideSearchComponent);
        component = fixture.componentInstance;
        component.course = course;
        fixture.componentRef.setInput('courseWideSearchConfig', courseWideSearchConfig);
        fixture.changeDetectorRef.detectChanges();
        component.isFetchingPosts.set(false);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    describe('when images load after the list was positioned at its end', () => {
        let container: HTMLElement;
        let top: number;
        let listHeight: number;

        const imageLoaded = () => {
            const image = document.createElement('img');
            container.appendChild(image);
            // load events do not bubble, so the component has to listen while the event travels down to the image
            image.dispatchEvent(new Event('load'));
        };

        beforeEach(() => {
            container = component.content()!.nativeElement;
            top = 0;
            listHeight = 1000;
            Object.defineProperties(container, {
                scrollHeight: { get: () => listHeight, configurable: true },
                clientHeight: { get: () => 400, configurable: true },
                scrollTop: {
                    get: () => top,
                    set: (value: number) => {
                        top = Math.max(0, Math.min(value, listHeight - 400));
                    },
                    configurable: true,
                },
            });
            // the freshly loaded posts are shown starting at the newest message
            component.handleScrollOnNewMessage();
            vi.runAllTimers();
        });

        it('should keep the newest message in view until the user scrolls', () => {
            expect(top).toBe(600);

            listHeight = 1500;
            imageLoaded();
            expect(top).toBe(1100);

            container.dispatchEvent(new Event('wheel'));
            listHeight = 2000;
            imageLoaded();
            expect(top).toBe(1100);
        });

        it('should hold the end again for the next search', () => {
            container.dispatchEvent(new Event('wheel'));
            component.onSearch();
            // the new results arrive
            component.isFetchingPosts.set(false);
            component.handleScrollOnNewMessage();
            vi.runAllTimers();

            listHeight = 1500;
            imageLoaded();
            expect(top).toBe(1100);
        });

        it('should not follow the end after the component is destroyed', () => {
            fixture.destroy();
            listHeight = 1500;
            imageLoaded();
            expect(top).toBe(600);
        });
    });

    describe('when result layout changes after initial positioning', () => {
        let resize: () => void;
        let observe: ReturnType<typeof vi.fn>;
        let disconnect: ReturnType<typeof vi.fn>;
        let container: HTMLElement;
        let top: number;
        let listHeight: number;
        let visibleHeight: number;

        beforeEach(() => {
            fixture.destroy();
            resize = () => {};
            observe = vi.fn();
            disconnect = vi.fn();
            vi.stubGlobal(
                'ResizeObserver',
                class {
                    constructor(callback: () => void) {
                        resize = callback;
                    }
                    observe = observe;
                    disconnect = disconnect;
                },
            );
            fixture = TestBed.createComponent(CourseWideSearchComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('courseWideSearchConfig', courseWideSearchConfig);
            fixture.detectChanges();
            component.isFetchingPosts.set(false);
            container = component.content()!.nativeElement;
            top = 0;
            listHeight = 1000;
            visibleHeight = 400;
            Object.defineProperties(container, {
                scrollHeight: { get: () => listHeight, configurable: true },
                clientHeight: { get: () => visibleHeight, configurable: true },
                scrollTop: {
                    get: () => top,
                    set: (value: number) => {
                        top = Math.max(0, Math.min(value, listHeight - visibleHeight));
                    },
                    configurable: true,
                },
            });
            component.handleScrollOnNewMessage();
            vi.runAllTimers();
        });

        afterEach(() => vi.unstubAllGlobals());

        it('holds the newest result through row-height changes and stops following after user input', () => {
            expect(top).toBe(600);
            listHeight = 1500;
            resize();
            expect(top).toBe(1100);

            container.dispatchEvent(new Event('wheel'));
            listHeight = 2000;
            resize();
            expect(top).toBe(1100);
        });

        it('holds the end through viewport resizing but does not override a pending older page', () => {
            visibleHeight = 600;
            resize();
            expect(top).toBe(400);

            component.totalNumberOfPosts.set(100);
            component.fetchNextPage();
            listHeight = 1500;
            resize();
            expect(top).toBe(400);
        });

        it.each([false, true])('keeps the newest result through automatic older-page prefetch (before initial positioning: %s)', (beforeInitialPositioning) => {
            if (beforeInitialPositioning) {
                container.dispatchEvent(new Event('wheel'));
                component.onSearch();
                component.isFetchingPosts.set(false);
            }
            component.totalNumberOfPosts.set(100);
            const request = vi.spyOn(communicationService, 'getFilteredPosts');
            component.fetchNextPage();
            const callbacks = request.mock.calls.at(-1)![3]!;
            callbacks.beforeApply();
            listHeight = 1500;
            callbacks.complete(true);
            vi.runAllTimers();
            expect(top).toBe(1100);
            listHeight = 2000;
            resize();
            expect(top).toBe(1600);

            // Deliberate reader input releases the end; subsequent older pages preserve that position.
            container.dispatchEvent(new Event('wheel'));
            top = 300;
            component.fetchNextPage();
            const olderPage = request.mock.calls.at(-1)![3]!;
            olderPage.beforeApply();
            listHeight = 2500;
            olderPage.complete(true);
            vi.runAllTimers();
            expect(top).toBe(800);
        });

        it('observes both result height and viewport size and disconnects on destruction', () => {
            expect(observe).toHaveBeenCalledWith(container);
            expect(observe).toHaveBeenCalledWith(fixture.nativeElement.querySelector('[data-testid="course-wide-search-results"]'));
            fixture.destroy();
            expect(disconnect).toHaveBeenCalledOnce();
        });
    });

    it('should leave the oldest search result reachable when all pages are loaded', () => {
        const container = component.content()!.nativeElement;
        container.scrollTop = 0;
        const fetchPosts = vi.spyOn(component, 'fetchPosts');
        component.totalNumberOfPosts.set(component.posts().length);

        component.fetchNextPage();

        expect(container.scrollTop).toBe(0);
        expect(fetchPosts).not.toHaveBeenCalled();
    });

    it('retries the same older page after a failed request', () => {
        component.totalNumberOfPosts.set(100);
        const request = vi.spyOn(communicationService, 'getFilteredPosts');
        component.fetchNextPage();
        expect(request.mock.calls.at(-1)![0].page).toBe(1);
        request.mock.calls.at(-1)![3]!.complete(false);
        expect(component.isFetchingPosts()).toBe(false);
        expect(component.page).toBe(1);

        component.fetchNextPage();
        expect(request).toHaveBeenCalledTimes(2);
        expect(request.mock.calls.at(-1)![0].page).toBe(1);
        request.mock.calls.at(-1)![3]!.complete(true);
        expect(component.page).toBe(2);
    });

    it('should preserve scrolling performed during a pending search page and ignore duplicate loads', () => {
        vi.runAllTimers();
        const container = component.content()!.nativeElement;
        Object.defineProperty(container, 'scrollHeight', { value: 1000, configurable: true });
        container.scrollTop = 20;
        component.totalNumberOfPosts.set(100);
        const fetchPosts = vi.spyOn(component, 'fetchPosts');
        const request = vi.spyOn(communicationService, 'getFilteredPosts');

        component.fetchNextPage();
        const callbacks = request.mock.calls.at(-1)?.[3];
        expect(callbacks).toBeDefined();
        // A cache/WebSocket emission must neither finish the request nor consume its anchor.
        component.setPosts([examplePost]);
        component.handleScrollOnNewMessage();
        vi.runAllTimers();
        expect(component.isFetchingPosts()).toBe(true);
        expect(container.scrollTop).toBe(20);
        component.fetchNextPage();
        expect(fetchPosts).toHaveBeenCalledOnce();
        expect(container.scrollTop).toBe(20);

        container.dispatchEvent(new Event('wheel'));
        container.scrollTop = 220; // The reader moves while the response is pending.
        callbacks!.beforeApply();
        component.setPosts([{ id: 2 } as Post, examplePost]);
        Object.defineProperty(container, 'scrollHeight', { value: 1600, configurable: true });
        callbacks!.complete(true);
        vi.runAllTimers();
        expect(container.scrollTop).toBe(820);

        container.scrollTop = 0;
        component.handleScrollOnNewMessage();
        vi.runAllTimers();
        expect(container.scrollTop).toBe(0);
    });

    it('should cancel an old positioning frame before starting a new search', () => {
        component.handleScrollOnNewMessage();
        component.onSearch();
        vi.runAllTimers();
        expect(component['initialScrollPositioned']).toBe(false);
    });

    it('should reset pagination when changing the search or its filters', () => {
        fixture.componentRef.setInput('courseWideSearchConfig', new CourseWideSearchConfig());
        fixture.detectChanges();
        component.page = 3;
        component.onSearch();
        expect(component.currentPostContextFilter?.page).toBe(0);
        component.page = 4;
        component.onChangeSortDir();
        expect(component.currentPostContextFilter?.page).toBe(0);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        component.handleScrollOnNewMessage();
    });

    it('should set initial values correctly', () => {
        expect(component.course).toBe(course);
        expect(component.posts()).toStrictEqual([examplePost]);
        expect(component.courseWideSearchConfig()).toBe(courseWideSearchConfig);
    });

    it('should initialize currentPostContextFilter correctly', () => {
        const conversationsOfUser = conversationDtoArray.map((conversation) => conversation.id);
        expect(component.currentPostContextFilter).toEqual({
            courseId: course.id,
            conversationIds: conversationsOfUser,
            searchText: undefined,
            filterToUnresolved: false,
            authorIds: [],
            filterToCourseWide: false,
            filterToAnsweredOrReacted: false,
            page: 0,
            pageSize: 50,
            pagingEnabled: true,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        });
    });

    it('should update currentPostContextFilter correctly', () => {
        const conversation = conversationDtoArray[0];

        courseWideSearchConfig.searchTerm = 'test';
        courseWideSearchConfig.selectedConversations = [conversation];
        component.onSearch();

        expect(component.currentPostContextFilter).toEqual({
            courseId: course.id,
            conversationIds: [conversation.id],
            authorIds: [],
            searchText: courseWideSearchConfig.searchTerm,
            filterToCourseWide: courseWideSearchConfig.filterToCourseWide,
            filterToUnresolved: courseWideSearchConfig.filterToUnresolved,
            filterToAnsweredOrReacted: courseWideSearchConfig.filterToAnsweredOrReacted,
            page: 0,
            pageSize: 50,
            pagingEnabled: true,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        });
    });

    it('should set conversationIds back to all conversations if no conversation is selected', () => {
        const singleConversation = conversationDtoArray[0];
        const allConversationIds = conversationDtoArray.map((conversation) => conversation.id);

        courseWideSearchConfig.selectedConversations = [singleConversation];
        component.onSearch();
        expect(component.currentPostContextFilter?.conversationIds).toEqual([singleConversation.id]);

        courseWideSearchConfig.selectedConversations = [];
        component.onSearch();
        expect(component.currentPostContextFilter?.conversationIds).toEqual(allConversationIds);
    });

    it('should fetch posts on next page fetch', () => {
        const getFilteredPostSpy = vi.spyOn(communicationService, 'getFilteredPosts');
        fixture.componentRef.setInput('courseWideSearchConfig', courseWideSearchConfig);
        component.totalNumberOfPosts.set(10);
        component.fetchNextPage();
        expect(getFilteredPostSpy).toHaveBeenCalledOnce();
    });

    it('should initialize formGroup correctly', () => {
        expect(component.formGroup.get('filterToCourseWide')?.value).toBe(false);
        expect(component.formGroup.get('filterToUnresolved')?.value).toBe(false);
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBe(false);
    });

    it('Should update filter setting when filterToCourseWide checkbox is checked', () => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: true,
            filterToUnresolved: false,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToCourseWide]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToCourseWide).toBe(true);
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBe(false);
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBe(false);
    });

    it('should disable the filterToCourseWide if a conversation is selected', () => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: true,
            filterToUnresolved: false,
            filterToAnsweredOrReacted: false,
        });
        component.courseWideSearchConfig().selectedConversations = [communicationGeneralChannelDTO];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        const filterResolvedCheckbox = component.formGroup.get('filterToCourseWide')!;
        expect(filterResolvedCheckbox.disabled).toBe(true);
        expect(filterResolvedCheckbox.value).toBe(false);
    });

    it('should re-enable the filterToCourseWide if no conversation is selected', () => {
        fixture.changeDetectorRef.detectChanges();
        component.courseWideSearchConfig().selectedConversations = [communicationGeneralChannelDTO];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        const filterResolvedCheckbox = component.formGroup.get('filterToCourseWide')!;
        expect(filterResolvedCheckbox.disabled).toBe(true);

        component.courseWideSearchConfig().selectedConversations = [];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        expect(filterResolvedCheckbox.disabled).toBe(false);
    });

    it('Should update filter setting when filterToUnresolved checkbox is checked', () => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: false,
            filterToUnresolved: true,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToCourseWide).toBe(false);
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBe(true);
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBe(false);
    });

    it('Should update filter setting when filterToAnsweredOrReacted checkbox is checked', () => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: false,
            filterToAnsweredOrReacted: true,
        });
        const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
        filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBe(false);
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBe(true);
    });

    it('Should update filter setting when all filter checkboxes are checked', () => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToAnsweredOrReacted: true,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBe(true);
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBe(true);
    });

    it('should initialize sorting direction correctly', () => {
        expect(component.courseWideSearchConfig()?.sortingOrder).toBe(SortDirection.DESCENDING);
    });

    it('should show the newest messages first by default', () => {
        // The list is displayed oldest to newest and starts at its end, so the page it shows must be the newest one.
        expect(new CourseWideSearchConfig().sortingOrder).toBe(SortDirection.DESCENDING);
        expect(component.sortingOrder()).toBe(SortDirection.DESCENDING);
    });

    it('should change sorting direction after clicking the order direction button', () => {
        fixture.changeDetectorRef.detectChanges();
        const selectedDirectionOption = getElement(fixture.debugElement, '.clickable');
        selectedDirectionOption.dispatchEvent(new Event('click'));
        expect(component.courseWideSearchConfig()?.sortingOrder).toBe(SortDirection.ASCENDING);
    });

    it('should change sorting direction once when a held Space key is released', () => {
        const sortControl = fixture.nativeElement.querySelector('[role="button"]') as HTMLElement;
        const initialDirection = component.sortingOrder();
        const changeDirection = vi.spyOn(component, 'onChangeSortDir');

        for (const [type, key] of [
            ['keydown', 'Enter'],
            ['keydown', ' '],
            ['keyup', ' '],
        ]) {
            const event = new KeyboardEvent(type, { key, bubbles: true, cancelable: true });
            sortControl.querySelector('fa-icon')!.dispatchEvent(event);
            expect(event.defaultPrevented).toBe(false);
        }
        expect(changeDirection).not.toHaveBeenCalled();

        for (const repeat of [false, true, true]) {
            const event = new KeyboardEvent('keydown', { key: ' ', repeat, bubbles: true, cancelable: true });
            sortControl.dispatchEvent(event);
            expect(event.defaultPrevented).toBe(true);
        }
        expect(changeDirection).not.toHaveBeenCalled();
        expect(component.sortingOrder()).toBe(initialDirection);

        sortControl.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        expect(changeDirection).toHaveBeenCalledOnce();
        expect(component.sortingOrder()).not.toBe(initialDirection);
    });
});
