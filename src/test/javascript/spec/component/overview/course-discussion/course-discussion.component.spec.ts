import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { getElement } from '../../../helpers/utils/general.utils';
import { By } from '@angular/platform-browser';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import {
    metisCourse,
    metisExamChannelDTO,
    metisExercise,
    metisExercise2,
    metisExerciseChannelDTO,
    metisGeneralChannelDTO,
    metisLecture,
    metisLecture2,
    metisLecture3,
    metisLectureChannelDTO,
    metisPostInChannel,
    metisUser1,
} from '../../../helpers/sample/metis-sample-data';
import { VirtualScrollComponent } from 'app/shared/virtual-scroll/virtual-scroll.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { MatSelectModule } from '@angular/material/select';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { MockMetisConversationService } from '../../../helpers/mocks/service/mock-metis-conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';

describe('CourseDiscussionComponent', () => {
    let component: CourseDiscussionComponent;
    let fixture: ComponentFixture<CourseDiscussionComponent>;
    let courseStorageService: CourseStorageService;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let metisServiceGetUserStub: jest.SpyInstance;
    let fetchNextPageSpy: jest.SpyInstance;

    const id = metisCourse.id;
    const parentRoute = {
        parent: {
            params: of({ id }),
            queryParams: of({ searchText: '' }),
        },
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockModule(NgbTooltipModule), MockModule(MatSelectModule)],
            declarations: [
                CourseDiscussionComponent,
                MockComponent(VirtualScrollComponent),
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(ItemCountComponent),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        })
            .compileComponents()
            .then(() => {
                courseStorageService = TestBed.inject(CourseStorageService);

                const metisConversationService = TestBed.inject(MetisConversationService);
                Object.defineProperty(metisConversationService, 'isServiceSetup$', {
                    get: () => new BehaviorSubject(true).asObservable(),
                });
                Object.defineProperty(metisConversationService, 'conversationsOfUser$', {
                    get: () => new BehaviorSubject([metisGeneralChannelDTO, metisExerciseChannelDTO, metisLectureChannelDTO, metisExamChannelDTO]).asObservable(),
                });

                jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(metisCourse));
                fixture = TestBed.createComponent(CourseDiscussionComponent);
                component = fixture.componentInstance;
                metisService = fixture.debugElement.injector.get(MetisService);
                metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
                metisServiceGetUserStub = jest.spyOn(metisService, 'getUser');
                fetchNextPageSpy = jest.spyOn(component, 'fetchNextPage');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and posts for course on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.course).toBe(metisCourse);
        expect(component.createdPost).not.toBeNull();
        expect(component.posts).toEqual([metisPostInChannel]);
        expect(component.currentPostContextFilter).toEqual({
            courseId: metisCourse.id,
            courseWideChannelIds: [],
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        });
    }));

    it('should initialize formGroup correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual([]);
        expect(component.formGroup.get('sortBy')?.value).toBe(PostSortCriterion.CREATION_DATE);
        expect(component.formGroup.get('filterToUnresolved')?.value).toBeFalse();
        expect(component.formGroup.get('filterToOwn')?.value).toBeFalse();
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBeFalse();
        expect(component.currentSortDirection).toBe(SortDirection.DESCENDING);
    }));

    it('should initialize overview page with course posts for default settings correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual([]);
        expect(component.formGroup.get('sortBy')?.value).toEqual(PostSortCriterion.CREATION_DATE);
        expect(component.currentSortDirection).toBe(SortDirection.DESCENDING);
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        expect(searchInput.textContent).toBe('');
        const contextOptions = getElement(fixture.debugElement, 'mat-select[name=context]');
        expect(component.lectures).toEqual([metisLecture, metisLecture2, metisLecture3]);
        expect(component.exercises).toEqual([metisExercise, metisExercise2]);
        expect(component.courseWideChannels).toEqual([metisGeneralChannelDTO, metisExerciseChannelDTO, metisLectureChannelDTO, metisExamChannelDTO]);
        // select should provide all context options
        expect(contextOptions.textContent).toContain(metisGeneralChannelDTO.name);
        expect(contextOptions.textContent).toContain(metisExerciseChannelDTO.name);
        expect(contextOptions.textContent).toContain(metisLectureChannelDTO.name);
        expect(contextOptions.textContent).toContain(metisExamChannelDTO.name);
        // nothing should be selected
        const selectedContextOption = getElement(fixture.debugElement, 'mat-select[name=context]');
        expect(selectedContextOption.value).toBeUndefined();
        // creation date should be selected as sort criterion
        const selectedSortByOption = getElement(fixture.debugElement, 'select[name=sortBy]');
        expect(selectedSortByOption.value).not.toBeNull();
        // descending should be selected as sort direction
        // show correct number of posts found
        const postCountInformation = getElement(fixture.debugElement, '.post-result-information');
        expect(component.posts).toEqual([metisPostInChannel]);
        expect(postCountInformation.textContent).not.toBeNull();
    }));

    it('should invoke metis service forcing a reload from server when search text changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        component.searchText = 'textToSearch';
        component.onSelectContext();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({
            courseId: metisCourse.id,
            courseWideChannelIds: [],
            searchText: component.searchText,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            page: component.page - 1,
            pageSize: component.itemsPerPage,
            pagingEnabled: true,
            postSortCriterion: 'CREATION_DATE',
            sortingOrder: 'DESCENDING',
        });
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(2);
    }));

    it('should invoke metis service and update filter setting when filterToUnresolved checkbox is checked', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.currentPostContextFilter.filterToUnresolved).toBeTrue();
        // actual post filtering done at server side, tested by AnswerPostIntegrationTest
    }));

    it('should invoke metis service and update filter setting when filterToUnresolved and filterToOwn checkbox is checked', fakeAsync(() => {
        const currentUser = metisUser1;
        metisServiceGetUserStub.mockReturnValue(currentUser);
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToOwn: true,
            filterToAnsweredOrReacted: false,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        filterOwnCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(4);
        expect(component.currentPostContextFilter.filterToUnresolved).toBeTrue();
        expect(component.currentPostContextFilter.filterToOwn).toBeTrue();
        expect(component.currentPostContextFilter.filterToAnsweredOrReacted).toBeFalse();
        // actual post filtering done at server side, tested by AnswerPostIntegrationTest
    }));

    it('should invoke metis service and update filter setting when filterToOwn checkbox is checked', fakeAsync(() => {
        const currentUser = metisUser1;
        metisServiceGetUserStub.mockReturnValue(currentUser);
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: false,
            filterToOwn: true,
            filterToAnsweredOrReacted: false,
        });
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        filterOwnCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.currentPostContextFilter.filterToUnresolved).toBeFalse();
        expect(component.currentPostContextFilter.filterToOwn).toBeTrue();
        expect(component.currentPostContextFilter.filterToAnsweredOrReacted).toBeFalse();
        // actual post filtering done at server side, tested by PostIntegrationTest
    }));

    it('should fetch new posts when context filter changes to conversation', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            context: [
                {
                    conversationId: metisPostInChannel.conversation!.id!,
                },
            ],
        });
        const contextOptions = fixture.debugElement.query(By.css('mat-select[name=context]'));
        contextOptions.triggerEventHandler('selectionChange', false);
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.posts).toEqual([metisPostInChannel]);
    }));

    it('should fetch new posts when multiple context filters are selected', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            context: [
                {
                    conversationId: 1,
                },
                {
                    conversationId: 2,
                },
            ],
        });
        const contextOptions = fixture.debugElement.query(By.css('mat-select[name=context]'));
        contextOptions.triggerEventHandler('selectionChange', false);
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(metisServiceGetFilteredPostsSpy.mock.calls[2][0]).toEqual({
            ...component.currentPostContextFilter,
        });
    }));

    it('should invoke metis service forcing a reload when sort criterion changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const sortByOptions = getElement(fixture.debugElement, 'select[name=sortBy]');
        sortByOptions.dispatchEvent(new Event('change'));
        expectGetFilteredPostsToBeCalled();
    }));

    it('should invoke metis service forcing a reload when sort direction changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const selectedDirectionOption = getElement(fixture.debugElement, '.clickable');
        selectedDirectionOption.dispatchEvent(new Event('click'));
        expectGetFilteredPostsToBeCalled();
    }));

    it('should fetch next page of posts if exists', fakeAsync(() => {
        component.itemsPerPage = 1;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.totalItems = 2;
        component.fetchNextPage();
        // next page does not exist, service method won't be called again
        component.fetchNextPage();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenNthCalledWith(
            3,
            {
                ...component.currentPostContextFilter,
                page: 1,
                pageSize: component.itemsPerPage,
                pagingEnabled: true,
            },
            false,
        );
    }));

    it('should call fetchNextPage when virtual scroller component renders last part of fetched posts', fakeAsync(() => {
        prepareComponent();

        const onEndOfOriginalItemsReachedEvent = new CustomEvent('onEndOfOriginalItemsReached');

        const scrollableDiv = getElement(fixture.debugElement, 'jhi-virtual-scroll');
        scrollableDiv.dispatchEvent(onEndOfOriginalItemsReachedEvent);

        expect(fetchNextPageSpy).toHaveBeenCalledOnce();
    }));

    function expectGetFilteredPostsToBeCalled() {
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({
            courseId: metisCourse.id,
            courseWideChannelIds: [],
            page: component.page - 1,
            pageSize: component.itemsPerPage,
            pagingEnabled: true,
            postSortCriterion: 'CREATION_DATE',
            sortingOrder: 'DESCENDING',
        });
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
    }

    describe('sorting of posts', () => {
        it('should distinguish context filter options for properly show them in form', () => {
            let result = component.compareContextFilterOptionFn({ conversationId: 1 }, { conversationId: 1 });
            expect(result).toBeTrue();
            result = component.compareContextFilterOptionFn({ conversationId: 1 }, { conversationId: 2 });
            expect(result).toBeFalse();
        });
    });

    function prepareComponent() {
        component.itemsPerPage = 5;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
    }
});
