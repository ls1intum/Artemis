import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Directive, EventEmitter, Input, Output } from '@angular/core';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { MetisService } from 'app/communication/service/metis.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { BehaviorSubject } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { metisExamChannelDTO, metisExerciseChannelDTO, metisGeneralChannelDTO, metisLectureChannelDTO } from 'test/helpers/sample/metis-sample-data';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Directive({
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
    courseWideSearchConfig.filterToCourseWide = false;
    courseWideSearchConfig.filterToAnsweredOrReacted = false;
    courseWideSearchConfig.sortingOrder = SortDirection.ASCENDING;
    courseWideSearchConfig.selectedConversations = [];
    courseWideSearchConfig.selectedAuthors = [];

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule, FaIconComponent],
            declarations: [
                CourseWideSearchComponent,
                InfiniteScrollStubDirective,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(PostingThreadComponent),
                MockComponent(MessageInlineInputComponent),
                MockComponent(PostCreateEditModalComponent),
                MockDirective(TranslateDirective),
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
        fixture.componentRef.setInput('courseWideSearchConfig', courseWideSearchConfig);
        fixture.changeDetectorRef.detectChanges();
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
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.course).toBe(course);
        expect(component.posts).toStrictEqual([examplePost]);
        expect(component.courseWideSearchConfig()).toBe(courseWideSearchConfig);
    }));

    it('should initialize currentPostContextFilter correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
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
            sortingOrder: SortDirection.ASCENDING,
        });
    }));

    it('should update currentPostContextFilter correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
        const conversation = conversationDtoArray[0];

        courseWideSearchConfig.searchTerm = 'test';
        courseWideSearchConfig.selectedConversations = [conversation];
        component.onSearch();
        tick();
        fixture.changeDetectorRef.detectChanges();

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
            sortingOrder: SortDirection.ASCENDING,
        });
    }));

    it('should set conversationIds back to all conversations if no conversation is selected', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
        const singleConversation = conversationDtoArray[0];
        const allConversationIds = conversationDtoArray.map((conversation) => conversation.id);

        courseWideSearchConfig.selectedConversations = [singleConversation];
        component.onSearch();
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.currentPostContextFilter?.conversationIds).toEqual([singleConversation.id]);

        courseWideSearchConfig.selectedConversations = [];
        component.onSearch();
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.currentPostContextFilter?.conversationIds).toEqual(allConversationIds);
    }));

    it('should fetch posts on next page fetch', fakeAsync(() => {
        const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
        fixture.componentRef.setInput('courseWideSearchConfig', courseWideSearchConfig);
        component.totalNumberOfPosts = 10;
        component.fetchNextPage();
        expect(getFilteredPostSpy).toHaveBeenCalledOnce();
    }));

    it('should initialize formGroup correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.formGroup.get('filterToCourseWide')?.value).toBeFalse();
        expect(component.formGroup.get('filterToUnresolved')?.value).toBeFalse();
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBeFalse();
    }));

    it('Should update filter setting when filterToCourseWide checkbox is checked', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: true,
            filterToUnresolved: false,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToCourseWide]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToCourseWide).toBeTrue();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBeFalse();
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBeFalse();
    }));

    it('should disable the filterToCourseWide if a conversation is selected', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: true,
            filterToUnresolved: false,
            filterToAnsweredOrReacted: false,
        });
        component.courseWideSearchConfig().selectedConversations = [metisGeneralChannelDTO];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        const filterResolvedCheckbox = component.formGroup.get('filterToCourseWide')!;
        expect(filterResolvedCheckbox.disabled).toBeTrue();
        expect(filterResolvedCheckbox.value).toBeFalse();
    }));

    it('should re-enable the filterToCourseWide if no conversation is selected', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.courseWideSearchConfig().selectedConversations = [metisGeneralChannelDTO];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        const filterResolvedCheckbox = component.formGroup.get('filterToCourseWide')!;
        expect(filterResolvedCheckbox.disabled).toBeTrue();

        component.courseWideSearchConfig().selectedConversations = [];
        component.onSearchConfigSelectionChange();
        fixture.changeDetectorRef.detectChanges();

        expect(filterResolvedCheckbox.disabled).toBeFalse();
    }));

    it('Should update filter setting when filterToUnresolved checkbox is checked', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToCourseWide: false,
            filterToUnresolved: true,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToCourseWide).toBeFalse();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBeTrue();
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBeFalse();
    }));

    it('Should update filter setting when filterToAnsweredOrReacted checkbox is checked', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: false,
            filterToAnsweredOrReacted: true,
        });
        const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
        filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBeFalse();
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBeTrue();
    }));

    it('Should update filter setting when all filter checkboxes are checked', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToAnsweredOrReacted: true,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.filterToUnresolved).toBeTrue();
        expect(component.courseWideSearchConfig()?.filterToAnsweredOrReacted).toBeTrue();
    }));

    it('should initialize sorting direction correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseWideSearchConfig()?.sortingOrder).toBe(SortDirection.ASCENDING);
    }));

    it('should change sorting direction after clicking the order direction button', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.changeDetectorRef.detectChanges();
        const selectedDirectionOption = getElement(fixture.debugElement, '.clickable');
        selectedDirectionOption.dispatchEvent(new Event('click'));
        expect(component.courseWideSearchConfig()?.sortingOrder).toBe(SortDirection.DESCENDING);
    }));
});
