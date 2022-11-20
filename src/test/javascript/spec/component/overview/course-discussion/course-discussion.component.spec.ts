import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseWideContext, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { getElement } from '../../../helpers/utils/general.utils';
import { NgbPaginationModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
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
    metisCoursePosts,
    metisCoursePostsWithCourseWideContext,
    metisExercise,
    metisExercise2,
    metisExercisePosts,
    metisLecture,
    metisLecture2,
    metisLecturePosts,
    metisUser1,
} from '../../../helpers/sample/metis-sample-data';
import { VirtualScrollComponent } from 'app/shared/virtual-scroll/virtual-scroll.component';

describe('CourseDiscussionComponent', () => {
    let component: CourseDiscussionComponent;
    let fixture: ComponentFixture<CourseDiscussionComponent>;
    let courseManagementService: CourseManagementService;
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
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule), MockModule(NgbPaginationModule)],
            declarations: [
                CourseDiscussionComponent,
                MockComponent(VirtualScrollComponent),
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(ButtonComponent),
                MockComponent(ItemCountComponent),
            ],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
            ],
        })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
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
        expect(component.posts).toEqual(metisCoursePosts);
        expect(component.currentPostContextFilter).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
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
        expect(component.formGroup.get('context')?.value).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        });
        expect(component.formGroup.get('sortBy')?.value).toBe(PostSortCriterion.CREATION_DATE);
        expect(component.formGroup.get('filterToUnresolved')?.value).toBeFalse();
        expect(component.formGroup.get('filterToOwn')?.value).toBeFalse();
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBeFalse();
    }));

    it('should initialize overview page with course posts for default settings correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
        });
        expect(component.formGroup.get('sortBy')?.value).toEqual(PostSortCriterion.CREATION_DATE);
        expect(component.currentSortDirection).toBe(SortDirection.DESCENDING);
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        expect(searchInput.textContent).toBe('');
        const contextOptions = getElement(fixture.debugElement, 'select[name=context]');
        expect(component.lectures).toEqual([metisLecture, metisLecture2]);
        expect(component.exercises).toEqual([metisExercise, metisExercise2]);
        // select should provide all context options
        expect(contextOptions.textContent).toContain(metisCourse.title);
        expect(contextOptions.textContent).toContain(metisLecture.title);
        expect(contextOptions.textContent).toContain(metisLecture2.title);
        expect(contextOptions.textContent).toContain(metisExercise.title);
        expect(contextOptions.textContent).toContain(metisExercise2.title);
        // course should be selected
        const selectedContextOption = getElement(fixture.debugElement, 'select[name=context]');
        expect(selectedContextOption.value).toContain(metisCourse.title);
        // creation date should be selected as sort criterion
        const selectedSortByOption = getElement(fixture.debugElement, 'select[name=sortBy]');
        expect(selectedSortByOption.value).not.toBeNull();
        // descending should be selected as sort direction
        // show correct number of posts found
        const postCountInformation = getElement(fixture.debugElement, '.post-result-information');
        expect(component.posts).toEqual(metisCoursePosts);
        expect(postCountInformation.textContent).not.toBeNull();
    }));

    it('should invoke metis service forcing a reload from server when search text changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        component.searchText = 'textToSearch';
        component.onSelectContext();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
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

    it('should fetch new posts when context filter changes to course-wide-context', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            context: {
                courseId: undefined,
                courseWideContext: CourseWideContext.ORGANIZATION,
                exerciseId: undefined,
                lectureId: undefined,
            },
        });
        const contextOptions = getElement(fixture.debugElement, 'select[name=context]');
        contextOptions.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.posts).toEqual(metisCoursePostsWithCourseWideContext.filter((post) => post.courseWideContext === CourseWideContext.ORGANIZATION));
    }));

    it('should fetch new posts when context filter changes to exercise', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            context: {
                courseId: undefined,
                courseWideContext: undefined,
                exerciseId: metisExercise.id,
                lectureId: undefined,
            },
        });
        const contextOptions = getElement(fixture.debugElement, 'select[name=context]');
        contextOptions.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.posts).toEqual(metisExercisePosts);
    }));

    it('should fetch new posts when context filter changes to lecture', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            context: {
                courseId: undefined,
                courseWideContext: undefined,
                exerciseId: undefined,
                lectureId: metisLecture.id,
            },
        });
        const contextOptions = getElement(fixture.debugElement, 'select[name=context]');
        contextOptions.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
        expect(component.posts).toEqual(metisLecturePosts);
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
        component.itemsPerPage = 5;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
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

    it('should not call fetchNextPage when rendering first fetched posts', fakeAsync(() => {
        component.itemsPerPage = 5;
        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const onItemsRenderEvent = new CustomEvent('onItemsRender');
        onItemsRenderEvent['items'] = component.posts.slice(0, 2);
        onItemsRenderEvent['length'] = 2;
        onItemsRenderEvent['startIndex'] = 0;
        onItemsRenderEvent['endIndex'] = 1;

        const scrollableDiv = getElement(fixture.debugElement, 'virtual-scroll');
        scrollableDiv.dispatchEvent(onItemsRenderEvent);

        expect(fetchNextPageSpy).toHaveBeenCalledTimes(0);
    }));

    it('should call fetchNextPage when rendering last part of fetched posts', fakeAsync(() => {
        prepareComponent();

        const onEndOfOriginalItemsReachedEvent = new CustomEvent('onEndOfOriginalItemsReached');

        const scrollableDiv = getElement(fixture.debugElement, 'virtual-scroll');
        scrollableDiv.dispatchEvent(onEndOfOriginalItemsReachedEvent);

        expect(fetchNextPageSpy).toHaveBeenCalledOnce();
    }));

    function expectGetFilteredPostsToBeCalled() {
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
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
            let result = component.compareContextFilterOptionFn({ courseId: metisCourse.id }, { courseId: metisCourse.id });
            expect(result).toBeTrue();
            result = component.compareContextFilterOptionFn({ courseId: metisCourse.id }, { courseId: 99 });
            expect(result).toBeFalse();
            result = component.compareContextFilterOptionFn({ lectureId: metisLecture.id }, { lectureId: metisLecture.id });
            expect(result).toBeTrue();
            result = component.compareContextFilterOptionFn({ lectureId: metisLecture.id }, { lectureId: 99 });
            expect(result).toBeFalse();
            result = component.compareContextFilterOptionFn({ exerciseId: metisExercise.id }, { exerciseId: metisExercise.id });
            expect(result).toBeTrue();
            result = component.compareContextFilterOptionFn({ exerciseId: metisExercise.id }, { exerciseId: 99 });
            expect(result).toBeFalse();
            result = component.compareContextFilterOptionFn({ courseWideContext: CourseWideContext.ORGANIZATION }, { courseWideContext: CourseWideContext.ORGANIZATION });
            expect(result).toBeTrue();
            result = component.compareContextFilterOptionFn({ courseWideContext: CourseWideContext.ORGANIZATION }, { courseWideContext: CourseWideContext.TECH_SUPPORT });
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
