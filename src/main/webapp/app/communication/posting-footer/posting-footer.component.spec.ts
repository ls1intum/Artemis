import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MetisService } from 'app/communication/service/metis.service';
import { PostService } from 'app/communication/service/post.service';
import { MockPostService } from 'test/helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { MockAnswerPostService } from 'test/helpers/mocks/service/mock-answer-post.service';
import { PostComponent } from 'app/communication/post/post.component';
import { AnswerPostComponent } from 'app/communication/answer-post/answer-post.component';
import { AnswerPostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { metisPostExerciseUser1, post, unApprovedAnswerPost1, unApprovedAnswerPost2, unsortedAnswerArray } from 'test/helpers/sample/metis-sample-data';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { signal } from '@angular/core';
import { PostingFooterComponent } from 'app/communication/posting-footer/posting-footer.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { DialogService } from 'primeng/dynamicdialog';

interface PostGroup {
    author: User | undefined;
    posts: Post[];
}

describe('PostingFooterComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PostingFooterComponent;
    let fixture: ComponentFixture<PostingFooterComponent>;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
            imports: [
                PostingFooterComponent,
                MockComponent(FaIconComponent),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        });
        TestBed.overrideComponent(PostingFooterComponent, {
            remove: { imports: [AnswerPostComponent, AnswerPostCreateEditModalComponent] },
            add: { imports: [MockComponent(AnswerPostComponent), MockComponent(AnswerPostCreateEditModalComponent)] },
        });
        fixture = TestBed.createComponent(PostingFooterComponent);
        component = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        metisServiceUserAuthorityStub = vi.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be initialized correctly for users that are at least tutors in course', () => {
        post.answers = unsortedAnswerArray;
        fixture.componentRef.setInput('posting', post);
        metisServiceUserAuthorityStub.mockReturnValue(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBe(true);
        expect(component.createdAnswerPost.resolvesPost).toBe(true);
    });

    it('should group answer posts correctly', () => {
        fixture.componentRef.setInput('sortedAnswerPosts', [unApprovedAnswerPost1, unApprovedAnswerPost2]);
        fixture.detectChanges();

        component.groupAnswerPosts();
        expect(component.groupedAnswerPosts.length).toBeGreaterThan(0);
        expect(component.groupedAnswerPosts[0].posts.length).toBeGreaterThan(0);
    });

    it('should group answer posts and detect changes on changes to sortedAnswerPosts input', () => {
        fixture.componentRef.setInput('sortedAnswerPosts', [unApprovedAnswerPost1, unApprovedAnswerPost2]);
        fixture.detectChanges();

        const changeDetectorSpy = vi.spyOn(component['changeDetector'], 'detectChanges');

        const answerPostWithDate = { ...unApprovedAnswerPost1, id: 3, creationDate: dayjs().subtract(2, 'day') } as AnswerPost;
        fixture.componentRef.setInput('sortedAnswerPosts', [unApprovedAnswerPost1, unApprovedAnswerPost2, answerPostWithDate]);
        fixture.detectChanges();
        expect(component.groupedAnswerPosts.length).toBeGreaterThan(0);
        expect(changeDetectorSpy).toHaveBeenCalled();
    });

    it('should clear answerPostCreateEditModal container on destroy', () => {
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('sortedAnswerPosts', [unApprovedAnswerPost1]);
        fixture.componentRef.setInput('showAnswers', true);
        fixture.detectChanges();

        const modal = component.answerPostCreateEditModal();
        if (modal) {
            const mockContainerRef = { clear: vi.fn() } as any;
            const containerRefSignal = signal(mockContainerRef);
            Object.defineProperty(modal, 'createEditAnswerPostContainerRef', { value: containerRefSignal });
            component.ngOnDestroy();
            expect(mockContainerRef.clear).toHaveBeenCalled();
        } else {
            // If no modal rendered, ngOnDestroy should not fail
            component.ngOnDestroy();
        }
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
        expect(result).toBe(true);
    });

    it('should return false if the post is not the last post in the group in isLastPost', () => {
        const mockPost: AnswerPost = { id: 100 } as AnswerPost;
        const mockGroup: PostGroup = {
            author: { id: 1, login: 'user1' } as User,
            posts: [mockPost, { id: 300, author: { id: 1 } } as AnswerPost],
        };

        const result = component.isLastPost(mockGroup, mockPost);
        expect(result).toBe(false);
    });

    it('should be initialized correctly for users that are not at least tutors in course', () => {
        post.answers = unsortedAnswerArray;
        fixture.componentRef.setInput('posting', post);
        metisServiceUserAuthorityStub.mockReturnValue(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBe(false);
        expect(component.createdAnswerPost.resolvesPost).toBe(false);
    });

    it('should open create answer post modal', () => {
        fixture.componentRef.setInput('posting', metisPostExerciseUser1);
        component.ngOnInit();
        fixture.detectChanges();

        const modalComponent = component.createAnswerPostModalComponent();
        const createAnswerPostModalOpen = vi.spyOn(modalComponent, 'open');
        component.openCreateAnswerPostModal();
        expect(createAnswerPostModalOpen).toHaveBeenCalledOnce();
    });

    it('should close create answer post modal', () => {
        fixture.componentRef.setInput('posting', metisPostExerciseUser1);
        component.ngOnInit();
        fixture.detectChanges();

        const modalComponent = component.createAnswerPostModalComponent();
        const createAnswerPostModalClose = vi.spyOn(modalComponent, 'close');
        component.closeCreateAnswerPostModal();
        expect(createAnswerPostModalClose).toHaveBeenCalledOnce();
    });

    it('should group answer posts correctly based on author and time difference', () => {
        const authorA: User = { id: 1, login: 'authorA' } as User;
        const authorB: User = { id: 2, login: 'authorB' } as User;

        const baseTime = dayjs();

        const post1: AnswerPost = { id: 1, author: authorA, creationDate: baseTime } as unknown as AnswerPost;
        const post2: AnswerPost = { id: 2, author: authorA, creationDate: baseTime.add(3, 'minute') } as unknown as AnswerPost;
        const post3: AnswerPost = { id: 3, author: authorA, creationDate: baseTime.add(10, 'minute') } as unknown as AnswerPost;
        const post4: AnswerPost = { id: 4, author: authorB, creationDate: baseTime.add(12, 'minute') } as unknown as AnswerPost;
        const post5: AnswerPost = { id: 5, author: authorB, creationDate: baseTime.add(14, 'minute') } as unknown as AnswerPost;
        fixture.componentRef.setInput('sortedAnswerPosts', [post3, post1, post5, post2, post4]);
        fixture.changeDetectorRef.detectChanges();

        component.groupAnswerPosts();
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

    it('should handle empty answer posts array', () => {
        fixture.componentRef.setInput('sortedAnswerPosts', []);
        component.groupAnswerPosts();
        expect(component.groupedAnswerPosts).toHaveLength(0);
    });
});
