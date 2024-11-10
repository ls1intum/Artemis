import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../../helpers/mocks/service/mock-answer-post.service';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { metisPostExerciseUser1, post, unsortedAnswerArray } from '../../../../../helpers/sample/metis-sample-data';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';

interface PostGroup {
    author: User | undefined;
    posts: AnswerPost[];
}

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostFooterComponent,
                TranslatePipeMock,
                MockComponent(FaIconComponent),
                MockComponent(PostReactionsBarComponent),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be initialized correctly for users that are at least tutors in course', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBeTrue();
        expect(component.createdAnswerPost.resolvesPost).toBeTrue();
    });

    it('should group answer posts correctly', () => {
        component.sortedAnswerPosts = unsortedAnswerArray;
        component.groupAnswerPosts();
        expect(component.groupedAnswerPosts.length).toBeGreaterThan(0); // Ensure groups are created
        expect(component.groupedAnswerPosts[0].posts.length).toBeGreaterThan(0); // Ensure posts exist in groups
    });

    it('should group answer posts and detect changes on changes to sortedAnswerPosts input', () => {
        const changeDetectorSpy = jest.spyOn(component['changeDetector'], 'detectChanges');
        component.sortedAnswerPosts = unsortedAnswerArray;
        component.ngOnChanges({ sortedAnswerPosts: { currentValue: unsortedAnswerArray, previousValue: [], firstChange: true, isFirstChange: () => true } });
        expect(component.groupedAnswerPosts.length).toBeGreaterThan(0);
        expect(changeDetectorSpy).toHaveBeenCalled();
    });

    it('should clear answerPostCreateEditModal container on destroy', () => {
        const mockContainerRef = { clear: jest.fn() } as any;
        component.answerPostCreateEditModal = {
            createEditAnswerPostContainerRef: mockContainerRef,
        } as AnswerPostCreateEditModalComponent;

        const clearSpy = jest.spyOn(mockContainerRef, 'clear');
        component.ngOnDestroy();
        expect(clearSpy).toHaveBeenCalled();
    });

    it('should return the ID of the post in trackPostByFn', () => {
        const mockPost: AnswerPost = { id: 200 } as AnswerPost;

        const result = component.trackPostByFn(0, mockPost);
        expect(result).toBe(200);
    });

    it('should return the ID of the first post in the group in trackGroupByFn', () => {
        const mockGroup: PostGroup = {
            author: { id: 1, login: 'user1' } as User,
            posts: [{ id: 100, author: { id: 1 } } as AnswerPost],
        };

        const result = component.trackGroupByFn(0, mockGroup);
        expect(result).toBe(100);
    });

    it('should return true if the post is the last post in the group in isLastPost', () => {
        const mockPost: AnswerPost = { id: 300 } as AnswerPost;
        const mockGroup: PostGroup = {
            author: { id: 1, login: 'user1' } as User,
            posts: [{ id: 100, author: { id: 1 } } as AnswerPost, mockPost],
        };

        const result = component.isLastPost(mockGroup, mockPost);
        expect(result).toBeTrue();
    });

    it('should return false if the post is not the last post in the group in isLastPost', () => {
        const mockPost: AnswerPost = { id: 100 } as AnswerPost;
        const mockGroup: PostGroup = {
            author: { id: 1, login: 'user1' } as User,
            posts: [mockPost, { id: 300, author: { id: 1 } } as AnswerPost],
        };

        const result = component.isLastPost(mockGroup, mockPost);
        expect(result).toBeFalse();
    });

    it('should be initialized correctly for users that are not at least tutors in course', () => {
        component.posting = post;
        component.posting.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.mockReturnValue(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBeFalse();
        expect(component.createdAnswerPost.resolvesPost).toBeFalse();
    });

    it('should open create answer post modal', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const createAnswerPostModalOpen = jest.spyOn(component.createAnswerPostModalComponent, 'open');
        component.openCreateAnswerPostModal();
        expect(createAnswerPostModalOpen).toHaveBeenCalledOnce();
    });
});
