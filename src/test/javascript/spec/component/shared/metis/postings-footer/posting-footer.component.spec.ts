import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { metisPostExerciseUser1, post, unsortedAnswerArray } from '../../../../helpers/sample/metis-sample-data';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Injector, input, runInInjectionContext, signal } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { PostingFooterComponent } from 'app/shared/metis/posting-footer/posting-footer.component';
import { Post } from 'app/entities/metis/post.model';

interface PostGroup {
    author: User | undefined;
    posts: Post[];
}

describe('PostingFooterComponent', () => {
    let component: PostingFooterComponent;
    let fixture: ComponentFixture<PostingFooterComponent>;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: jest.SpyInstance;
    let injector: Injector;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostingFooterComponent,
                TranslatePipeMock,
                MockComponent(FaIconComponent),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingFooterComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                injector = fixture.debugElement.injector;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be initialized correctly for users that are at least tutors in course', () => {
        runInInjectionContext(injector, () => {
            post.answers = unsortedAnswerArray;
            component.posting = input<Posting>(post);
            metisServiceUserAuthorityStub.mockReturnValue(true);
            component.ngOnInit();
            expect(component.isAtLeastTutorInCourse).toBeTrue();
            expect(component.createdAnswerPost.resolvesPost).toBeTrue();
        });
    });

    it('should group answer posts correctly', () => {
        runInInjectionContext(injector, () => {
            component.sortedAnswerPosts = input<AnswerPost[]>(unsortedAnswerArray);
            component.groupAnswerPosts();
            const mockContainerRef = { clear: jest.fn() } as any;
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
            expect(component.groupedAnswerPosts.length).toBeGreaterThan(0);
            expect(component.groupedAnswerPosts[0].posts.length).toBeGreaterThan(0);
        });
    });

    it('should group answer posts and detect changes on changes to sortedAnswerPosts input', () => {
        runInInjectionContext(injector, () => {
            component.sortedAnswerPosts = input<AnswerPost[]>(unsortedAnswerArray);
            const changeDetectorSpy = jest.spyOn(component['changeDetector'], 'detectChanges');
            component.ngOnChanges({ sortedAnswerPosts: { currentValue: unsortedAnswerArray, previousValue: [], firstChange: true, isFirstChange: () => true } });
            const mockContainerRef = { clear: jest.fn() } as any;
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
            expect(component.groupedAnswerPosts.length).toBeGreaterThan(0);
            expect(changeDetectorSpy).toHaveBeenCalled();
        });
    });

    it('should clear answerPostCreateEditModal container on destroy', () => {
        const mockContainerRef = { clear: jest.fn() } as any;
        component.answerPostCreateEditModal = {
            createEditAnswerPostContainerRef: signal(mockContainerRef),
        } as unknown as AnswerPostCreateEditModalComponent;

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
        runInInjectionContext(injector, () => {
            post.answers = unsortedAnswerArray;
            component.posting = input<Posting>(post);
            metisServiceUserAuthorityStub.mockReturnValue(false);
            component.ngOnInit();
            expect(component.isAtLeastTutorInCourse).toBeFalse();
            expect(component.createdAnswerPost.resolvesPost).toBeFalse();
        });
    });

    it('should open create answer post modal', () => {
        runInInjectionContext(injector, () => {
            component.posting = input<Posting>(metisPostExerciseUser1);
            component.ngOnInit();
            fixture.detectChanges();
            const createAnswerPostModalOpen = jest.spyOn(component.createAnswerPostModalComponent, 'open');
            component.openCreateAnswerPostModal();
            expect(createAnswerPostModalOpen).toHaveBeenCalledOnce();
            const mockContainerRef = { clear: jest.fn() } as any;
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
        });
    });

    it('should close create answer post modal', () => {
        runInInjectionContext(injector, () => {
            component.posting = input<Posting>(metisPostExerciseUser1);
            component.ngOnInit();
            const mockContainerRef = { clear: jest.fn() } as any;
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
            fixture.detectChanges();
            const createAnswerPostModalClose = jest.spyOn(component.createAnswerPostModalComponent, 'close');
            component.closeCreateAnswerPostModal();
            expect(createAnswerPostModalClose).toHaveBeenCalledOnce();
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
        });
    });

    it('should group answer posts correctly based on author and time difference', () => {
        const authorA: User = { id: 1, login: 'authorA' } as User;
        const authorB: User = { id: 2, login: 'authorB' } as User;

        const baseTime = dayjs();

        const post1: AnswerPost = { id: 1, author: authorA, creationDate: baseTime.toDate() } as unknown as AnswerPost;
        const post2: AnswerPost = { id: 2, author: authorA, creationDate: baseTime.add(3, 'minute').toDate() } as unknown as AnswerPost;
        const post3: AnswerPost = { id: 3, author: authorA, creationDate: baseTime.add(10, 'minute').toDate() } as unknown as AnswerPost;
        const post4: AnswerPost = { id: 4, author: authorB, creationDate: baseTime.add(12, 'minute').toDate() } as unknown as AnswerPost;
        const post5: AnswerPost = { id: 5, author: authorB, creationDate: baseTime.add(14, 'minute').toDate() } as unknown as AnswerPost;
        runInInjectionContext(injector, () => {
            component.sortedAnswerPosts = input<AnswerPost[]>([post3, post1, post5, post2, post4]);

            component.groupAnswerPosts();
            const mockContainerRef = { clear: jest.fn() } as any;
            component.answerPostCreateEditModal = {
                createEditAnswerPostContainerRef: signal(mockContainerRef),
            } as unknown as AnswerPostCreateEditModalComponent;
            expect(component.groupedAnswerPosts).toHaveLength(3);

            const group1 = component.groupedAnswerPosts[0];
            expect(group1.author).toEqual(authorA);
            expect(group1.posts).toHaveLength(2);
            expect(group1.posts).toContainEqual(expect.objectContaining({ id: post1.id }));
            expect(group1.posts).toContainEqual(expect.objectContaining({ id: post2.id }));

            const group2 = component.groupedAnswerPosts[1];
            expect(group2.author).toEqual(authorA);
            expect(group2.posts).toHaveLength(1);
            expect(group2.posts).toContainEqual(expect.objectContaining({ id: post3.id }));

            const group3 = component.groupedAnswerPosts[2];
            expect(group3.author).toEqual(authorB);
            expect(group3.posts).toHaveLength(2);
            expect(group3.posts).toContainEqual(expect.objectContaining({ id: post4.id }));
            expect(group3.posts).toContainEqual(expect.objectContaining({ id: post5.id }));
        });
    });

    it('should handle empty answer posts array', () => {
        runInInjectionContext(injector, () => {
            component.sortedAnswerPosts = input<AnswerPost[]>([]);
            component.groupAnswerPosts();
            expect(component.groupedAnswerPosts).toHaveLength(0);
        });
    });
});
