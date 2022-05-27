import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import dayjs from 'dayjs/esm';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { DisplayPriority, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { getElement, getElements } from '../../../helpers/utils/general.utils';
import { ButtonComponent } from 'app/shared/components/button.component';
import {
    metisCourse,
    metisCoursePostsWithCourseWideContext,
    metisExercise,
    metisExercisePosts,
    metisLecture,
    metisLecturePosts,
    metisPostExerciseUser1,
    metisPostExerciseUser2,
    metisPostLectureUser1,
    metisPostLectureUser2,
    metisPostTechSupport,
    metisUpVoteReactionUser1,
} from '../../../helpers/sample/metis-sample-data';

describe('PageDiscussionSectionComponent', () => {
    let component: DiscussionSectionComponent;
    let fixture: ComponentFixture<DiscussionSectionComponent>;
    let courseManagementService: CourseManagementService;
    let metisService: MetisService;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let post4: Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: MetisService, useClass: MetisService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ postId: metisPostTechSupport.id, courseId: metisCourse.id }),
                },
            ],
            declarations: [
                DiscussionSectionComponent,
                MockComponent(PostingThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockComponent(FaIconComponent),
                MockComponent(ButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
        })
            .overrideComponent(DiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
                fixture = TestBed.createComponent(DiscussionSectionComponent);
                component = fixture.componentInstance;
                metisService = fixture.debugElement.injector.get(MetisService);
                metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and posts for exercise on initialization', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        expect(component.course).toEqual(metisCourse);
        expect(component.createdPost).toBeDefined();
        expect(component.posts).toEqual(metisExercisePosts);
    }));

    it('should set course and posts for lecture on initialization', fakeAsync(() => {
        component.lecture = metisLecture;
        component.ngOnInit();
        tick();
        expect(component.createdPost).toBeDefined();
        expect(component.posts).toEqual(metisLecturePosts);
    }));

    it('should show single post if current post is set', fakeAsync(() => {
        component.ngOnInit();
        tick();
        // mock activated route returns id of metisPostTechSupport
        expect(component.currentPost).toEqual(metisPostTechSupport);
    }));

    it('should reset current post', fakeAsync(() => {
        component.resetCurrentPost();
        tick();
        expect(component.currentPost).toEqual(undefined);
        expect(component.currentPostId).toEqual(undefined);
    }));

    it('should sort posts correctly', () => {
        post1 = metisPostExerciseUser1;
        post1.creationDate = dayjs();
        post1.displayPriority = DisplayPriority.PINNED;

        post2 = metisPostExerciseUser2;
        post2.creationDate = dayjs().subtract(1, 'day');
        post2.displayPriority = DisplayPriority.NONE;

        post3 = metisPostLectureUser1;
        post3.creationDate = dayjs().subtract(2, 'day');
        post3.reactions = [metisUpVoteReactionUser1];
        post3.displayPriority = DisplayPriority.NONE;

        post4 = metisPostLectureUser2;
        post4.creationDate = dayjs().subtract(2, 'minute');
        post4.reactions = [metisUpVoteReactionUser1];
        post4.displayPriority = DisplayPriority.ARCHIVED;

        let posts = [post1, post2, post3, post4];
        posts = posts.sort(component.sectionSortFn);
        expect(posts).toEqual([post1, post3, post2, post4]);
    });

    it('should initialize formGroup correctly for exercise or lecture', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: component.exercise?.id,
            lectureId: component.lecture?.id,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
        expect(component.formGroup.get('filterToUnresolved')?.value).toBe(false);
        expect(component.formGroup.get('filterToOwn')?.value).toBe(false);
        expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBe(false);
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        expect(searchInput.textContent).toBe('');
    }));

    it('should initialize discussion section page for exercise or lecture posts with default settings correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.formGroup.get('context')?.value).toEqual({
            courseId: metisCourse.id,
            courseWideContext: undefined,
            exerciseId: component.exercise?.id,
            lectureId: component.lecture?.id,
            searchText: undefined,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        expect(searchInput.textContent).toBe('');
    }));

    it('should display an extra new post button on top of posts for user convenience', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.posts = metisCoursePostsWithCourseWideContext;
        fixture.detectChanges();
        const newPostButtons = getElements(fixture.debugElement, '.btn-primary');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(2);
    }));

    it('extra new post button on top of posts should be hidden if there are less then 3 posts', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const newPostButtons = getElements(fixture.debugElement, '.btn-primary');
        expect(newPostButtons).not.toBeNull();
        expect(newPostButtons).toHaveLength(1);
    }));

    it('should show search-bar and filters if not focused to a post', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).not.toBeNull();
        expect(filterResolvedCheckbox).not.toBeNull();
        expect(filterOwnCheckbox).not.toBeNull();
        expect(filterToAnsweredOrReacted).not.toBeNull();
    }));

    it('should hide search-bar and filters if focused to a post', fakeAsync(() => {
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        expect(searchInput).toBeNull();
        expect(filterResolvedCheckbox).toBeNull();
        expect(filterOwnCheckbox).toBeNull();
        expect(filterToAnsweredOrReacted).toBeNull();
    }));

    it('triggering filters should invoke the metis service', fakeAsync(() => {
        component.exercise = metisExercise;
        component.ngOnInit();
        tick();
        fixture.detectChanges();
        component.formGroup.patchValue({
            filterToUnresolved: true,
            filterToOwn: true,
            filterToAnsweredOrReacted: true,
        });

        const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
        const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
        const filterToAnsweredOrReacted = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');

        filterResolvedCheckbox.dispatchEvent(new Event('change'));
        filterOwnCheckbox.dispatchEvent(new Event('change'));
        filterToAnsweredOrReacted.dispatchEvent(new Event('change'));

        tick();
        fixture.detectChanges();

        expect(component.currentPostContextFilter.filterToUnresolved).toBe(true);
        expect(component.currentPostContextFilter.filterToOwn).toBe(true);
        expect(component.currentPostContextFilter.filterToAnsweredOrReacted).toBe(true);
        expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledTimes(5);
    }));
});
