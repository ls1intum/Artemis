import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import { CourseWideContext, DisplayPriority, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { getElement } from '../../../helpers/utils/general.utils';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
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
import dayjs from 'dayjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
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
    metisPostExerciseUser1,
    metisPostExerciseUser2,
    metisPostLectureUser1,
    metisPostLectureUser2,
    metisResolvingAnswerPostUser1,
    metisUpVoteReactionUser1,
    metisUser1,
} from '../../../helpers/sample/metis-sample-data';

describe('CourseDiscussionComponent', () => {
    let component: CourseDiscussionComponent;
    let fixture: ComponentFixture<CourseDiscussionComponent>;
    let courseManagementService: CourseManagementService;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let metisServiceGetUserStub: jest.SpyInstance;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let post4: Post;
    let posts: Post[];

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
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [
                CourseDiscussionComponent,
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(ButtonComponent),
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and posts for course on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.course).toBe(metisCourse);
        expect(component.createdPost).not.toBe(null);
        expect(component.posts).toEqual(metisCoursePosts);
        expect(component.currentPostContextFilter).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
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
        });
        expect(component.formGroup.get('sortBy')?.value).toEqual(PostSortCriterion.CREATION_DATE);
    }));

    it('should initialize overview page with course posts for default settings correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: undefined,
            lectureId: undefined,
        });
        expect(component.formGroup.get('sortBy')?.value).toEqual(PostSortCriterion.CREATION_DATE);
        expect(component.currentSortDirection).toEqual(SortDirection.DESC);
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

    it('should invoke metis service without forcing a reload when search text changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        component.onSearch();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            {
                courseId: metisCourse.id,
                courseWideContext: undefined,
                exerciseId: undefined,
                lectureId: undefined,
            },
            false, // forceReload false
        );
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(2);
    }));

    it('should search for posts with certain id when pattern is used', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.searchText = '#1';
        component.onSearch();
        tick();
        fixture.detectChanges();
        expect(component.posts).toHaveLength(1);
    }));

    it('should invoke metis service, update filter setting and displayed posts when filterToUnresolved checkbox is checked', fakeAsync(() => {
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
        expect(component.filterToUnresolved).toBe(true);
        // one of the posts has an answer post that is has resolvesPost set to true, i.e. one post is resolved and therefore filtered out
        expect(component.posts).toHaveLength(metisCoursePosts.length - 1);
    }));

    it('should invoke metis service, update filter setting and displayed posts when filterToUnresolved and filterToOwn checkbox is checked', fakeAsync(() => {
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
        expect(component.filterToUnresolved).toBe(true);
        expect(component.filterToOwn).toBe(true);
        expect(component.filterToAnsweredOrReactedByUser).toBe(false);
        // determine expected posts
        const expectedPosts = metisCoursePosts.filter(
            (post: Post) => post.author === currentUser && !(post.answers && post.answers.some((answer: AnswerPost) => answer.resolvesPost === true)),
        );
        expect(component.posts).toHaveLength(expectedPosts.length);
    }));

    it('should invoke metis service, update filter setting and displayed posts when filterToOwn checkbox is checked', fakeAsync(() => {
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
        expect(component.filterToUnresolved).toBe(false);
        expect(component.filterToOwn).toBe(true);
        expect(component.filterToAnsweredOrReactedByUser).toBe(false);
        // determine expected posts
        const expectedPosts = metisCoursePosts.filter((post: Post) => post.author === currentUser);
        expect(component.posts).toHaveLength(expectedPosts.length);
    }));

    it('should invoke metis service, update filter setting and displayed posts when filterToUnresolved and filterToAnsweredOrReactedByUser checkbox is checked', fakeAsync(() => {
        const currentUser = metisUser1;
        metisServiceGetUserStub.mockReturnValue(currentUser);
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToOwn: false,
            filterToAnsweredOrReacted: true,
        });
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        tick();
        filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
        tick();
        fixture.detectChanges();
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(4);
        expect(component.filterToUnresolved).toBe(true);
        expect(component.filterToOwn).toBe(false);
        expect(component.filterToAnsweredOrReactedByUser).toBe(true);
        // determine expected posts
        const expectedPosts = metisCoursePosts.filter(
            (post: Post) =>
                ((post.answers && post.answers.some((answer: AnswerPost) => answer.author === currentUser)) ||
                    (post.reactions && post.reactions.some((reaction: Reaction) => reaction.user?.id! === currentUser.id))) &&
                !(post.answers && post.answers.some((answer: AnswerPost) => answer.resolvesPost === true)),
        );
        expect(component.posts).toHaveLength(expectedPosts.length);
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

    it('should invoke metis service without forcing a reload when sort criterion changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const sortByOptions = getElement(fixture.debugElement, 'select[name=sortBy]');
        sortByOptions.dispatchEvent(new Event('change'));
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            {
                courseId: metisCourse.id,
                courseWideContext: undefined,
                exerciseId: undefined,
                lectureId: undefined,
            },
            false, // forceReload false
        );
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
    }));

    it('should invoke metis service without forcing a reload when sort direction changed', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const selectedDirectionOption = getElement(fixture.debugElement, '.clickable');
        selectedDirectionOption.dispatchEvent(new Event('click'));
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith(
            {
                courseId: metisCourse.id,
                courseWideContext: undefined,
                exerciseId: undefined,
                lectureId: undefined,
            },
            false, // forceReload false
        );
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(3);
    }));

    describe('sorting of posts', () => {
        beforeEach(() => {
            post1 = metisPostExerciseUser1;
            post1.creationDate = dayjs();
            post1.displayPriority = DisplayPriority.PINNED;

            post2 = metisPostExerciseUser2;
            post2.creationDate = dayjs().subtract(1, 'day');
            post2.displayPriority = DisplayPriority.NONE;

            post3 = metisPostLectureUser1;
            post3.creationDate = dayjs().subtract(2, 'day');
            post3.reactions = [metisUpVoteReactionUser1];
            post3.answers = [metisResolvingAnswerPostUser1];
            post3.displayPriority = DisplayPriority.NONE;

            post4 = metisPostLectureUser2;
            post4.creationDate = dayjs().subtract(2, 'minute');
            post4.reactions = [metisUpVoteReactionUser1];
            post4.displayPriority = DisplayPriority.ARCHIVED;

            posts = [post1, post2, post3, post4];
        });

        it('should sort posts correctly by creation date desc', () => {
            component.currentSortCriterion = PostSortCriterion.CREATION_DATE;
            component.currentSortDirection = SortDirection.DESC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post2, post3, post4]);
        });

        it('should sort posts correctly by creation date asc', () => {
            component.currentSortCriterion = PostSortCriterion.CREATION_DATE;
            component.currentSortDirection = SortDirection.ASC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post3, post2, post4]);
        });

        it('should sort posts correctly by votes desc', () => {
            component.currentSortCriterion = PostSortCriterion.VOTES;
            component.currentSortDirection = SortDirection.DESC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post3, post2, post4]);
        });

        it('should sort posts correctly by votes asc', () => {
            component.currentSortCriterion = PostSortCriterion.VOTES;
            component.currentSortDirection = SortDirection.ASC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post2, post3, post4]);
        });

        it('should sort posts correctly by answer count desc', () => {
            component.currentSortCriterion = PostSortCriterion.ANSWER_COUNT;
            component.currentSortDirection = SortDirection.DESC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post3, post2, post4]);
        });

        it('should sort posts correctly by answer count asc', () => {
            component.currentSortCriterion = PostSortCriterion.ANSWER_COUNT;
            component.currentSortDirection = SortDirection.ASC;
            posts = posts.sort(component.overviewSortFn);
            // pinned is first, archived is last independent of sort criterion
            expect(posts).toEqual([post1, post2, post3, post4]);
        });

        it('should distinguish context filter options for properly show them in form', () => {
            let result = component.compareContextFilterOptionFn({ courseId: metisCourse.id }, { courseId: metisCourse.id });
            expect(result).toBe(true);
            result = component.compareContextFilterOptionFn({ courseId: metisCourse.id }, { courseId: 99 });
            expect(result).toBe(false);
            result = component.compareContextFilterOptionFn({ lectureId: metisLecture.id }, { lectureId: metisLecture.id });
            expect(result).toBe(true);
            result = component.compareContextFilterOptionFn({ lectureId: metisLecture.id }, { lectureId: 99 });
            expect(result).toBe(false);
            result = component.compareContextFilterOptionFn({ exerciseId: metisExercise.id }, { exerciseId: metisExercise.id });
            expect(result).toBe(true);
            result = component.compareContextFilterOptionFn({ exerciseId: metisExercise.id }, { exerciseId: 99 });
            expect(result).toBe(false);
            result = component.compareContextFilterOptionFn({ courseWideContext: CourseWideContext.ORGANIZATION }, { courseWideContext: CourseWideContext.ORGANIZATION });
            expect(result).toBe(true);
            result = component.compareContextFilterOptionFn({ courseWideContext: CourseWideContext.ORGANIZATION }, { courseWideContext: CourseWideContext.TECH_SUPPORT });
            expect(result).toBe(false);
        });
    });
});
