import { type Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SavedPostsComponent } from 'app/communication/course-conversations-components/saved-posts/saved-posts.component';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { Posting, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { EMPTY, of, throwError } from 'rxjs';
import { MockComponent, MockDirective } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingSummaryComponent } from 'app/communication/course-conversations-components/posting-summary/posting-summary.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';

describe('SavedPostsComponent', () => {
    let component: SavedPostsComponent;
    let fixture: ComponentFixture<SavedPostsComponent>;
    let savedPostService: Mocked<SavedPostService>;
    let alertService: Mocked<AlertService>;

    const mockPosting: Posting = {
        id: 1,
        content: 'Test Content',
    };

    const mockPostings: Posting[] = [mockPosting, { id: 2, content: 'Test Content 2' }];

    beforeEach(async () => {
        const mockSavedPostService = {
            fetchSavedPosts: vi.fn(),
            convertPostingToCorrespondingType: vi.fn((post) => post),
            changeSavedPostStatus: vi.fn(),
            removeSavedPost: vi.fn(),
        };

        const mockAlertService = {
            error: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [FaIconComponent, SavedPostsComponent, MockDirective(TranslateDirective), MockComponent(PostingSummaryComponent)],
            providers: [
                { provide: SavedPostService, useValue: mockSavedPostService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        savedPostService = TestBed.inject(SavedPostService) as Mocked<SavedPostService>;
        alertService = TestBed.inject(AlertService) as Mocked<AlertService>;
        fixture = TestBed.createComponent(SavedPostsComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Initialization', () => {
        it('should fetch saved posts successfully', () => {
            const courseId = 123;
            const status = SavedPostStatus.IN_PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: mockPostings })));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(savedPostService.fetchSavedPosts).toHaveBeenCalledWith(courseId, status);
            expect(component['posts']()).toEqual(mockPostings);
            expect(component['hiddenPosts']()).toEqual([]);
        });

        it('should handle empty response', () => {
            const courseId = 123;
            const status = SavedPostStatus.IN_PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: [] })));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(component['posts']()).toEqual([]);
        });

        it('should handle error response', () => {
            const courseId = 123;
            const status = SavedPostStatus.IN_PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(throwError(() => new Error('Test error')));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(component['posts']()).toEqual([]);
        });
    });

    describe('Change post status', () => {
        it('should update post status and add to hidden posts', () => {
            const newStatus = SavedPostStatus.ARCHIVED;
            savedPostService.changeSavedPostStatus.mockReturnValue(of({}));

            component['changeSavedPostStatus'](mockPosting, newStatus);

            expect(savedPostService.changeSavedPostStatus).toHaveBeenCalledWith(mockPosting, SavedPostStatus.ARCHIVED);
            expect(component['hiddenPosts']()).toContain(mockPosting.id);
        });

        it('should handle error when changing post status', () => {
            const newStatus = SavedPostStatus.ARCHIVED;
            savedPostService.changeSavedPostStatus.mockReturnValue(throwError(() => new Error('Test error')));

            component['changeSavedPostStatus'](mockPosting, newStatus);

            expect(savedPostService.changeSavedPostStatus).toHaveBeenCalledWith(mockPosting, SavedPostStatus.ARCHIVED);
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.communication.post.changeSavedStatusError');
        });
    });

    describe('Remove saved post', () => {
        it('should remove saved post and add to hidden posts', () => {
            savedPostService.removeSavedPost.mockReturnValue(of({}));
            component['removeSavedPost'](mockPosting);

            expect(savedPostService.removeSavedPost).toHaveBeenCalledWith(mockPosting);
            expect(component['hiddenPosts']()).toContain(mockPosting.id);
        });

        it('should handle error when removing saved post', () => {
            savedPostService.removeSavedPost.mockReturnValue(throwError(() => new Error('Test error')));
            component['removeSavedPost'](mockPosting);

            expect(savedPostService.removeSavedPost).toHaveBeenCalledWith(mockPosting);
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.communication.post.removeBookmarkError');
        });
    });

    describe('Navigation', () => {
        it('should emit post when navigating', () => {
            const emitSpy = vi.spyOn(component.onNavigateToPost, 'emit');

            component['onTriggerNavigateToPost'](mockPosting);

            expect(emitSpy).toHaveBeenCalledWith(mockPosting);
        });

        it('should open the sidebar when openSidebar is called', () => {
            const openSidebarSpy = vi.spyOn(TestBed.inject(CourseSidebarService), 'openSidebar');

            component['openSidebar']();

            expect(openSidebarSpy).toHaveBeenCalledOnce();
        });
    });

    describe('Template interaction', () => {
        beforeEach(async () => {
            const status = SavedPostStatus.IN_PROGRESS;
            fixture.componentRef.setInput('savedPostStatus', status);
            savedPostService.fetchSavedPosts.mockReturnValue(EMPTY);
        });

        it('should show empty notice when no posts are available', () => {
            component['posts'].set([]);
            fixture.componentRef.setInput('savedPostStatus', SavedPostStatus.IN_PROGRESS);
            fixture.componentRef.setInput('courseId', 1);
            fixture.detectChanges();

            const emptyNotice = fixture.nativeElement.querySelector('.saved-posts-empty-notice');
            expect(emptyNotice).toBeTruthy();
        });

        it('should not show empty notice when posts are available', () => {
            const courseId = 123;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: mockPostings })));
            fixture.componentRef.setInput('courseId', courseId);
            fixture.detectChanges();
            vi.advanceTimersByTime(0);
            fixture.detectChanges();

            const emptyNotice = fixture.nativeElement.querySelector('.saved-posts-empty-notice');
            expect(emptyNotice).toBeFalsy();
        });

        it('should show delete post notice when archived is selected', () => {
            fixture.componentRef.setInput('savedPostStatus', SavedPostStatus.ARCHIVED);
            fixture.componentRef.setInput('courseId', 1);
            fixture.detectChanges();
            vi.advanceTimersByTime(0);
            fixture.detectChanges();

            const optionsElement = fixture.nativeElement.querySelector('.saved-posts-delete-notice');
            expect(component['isShowDeleteNotice']()).toBeTruthy();
            expect(optionsElement).toBeTruthy();
        });

        it('should keep an overflowing saved-post list as the scroll container', () => {
            const overflowingPosts = Array.from({ length: 20 }, (_, index) => ({ id: index + 1, content: `Saved post ${index + 1}` }) as Posting);
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: overflowingPosts })));
            fixture.componentRef.setInput('courseId', 1);
            fixture.detectChanges();

            const host = fixture.nativeElement as HTMLElement;
            const wrapper = host.querySelector<HTMLElement>('.saved-posts')!;
            const heading = wrapper.querySelector<HTMLElement>('h4')!;
            const postContainer = host.querySelector<HTMLElement>('.saved-posts-post-container')!;
            const renderedPosts = postContainer.querySelectorAll<HTMLElement>('jhi-posting-summary');
            const lastPost = renderedPosts.item(renderedPosts.length - 1);
            Object.defineProperties(postContainer, {
                clientHeight: { configurable: true, value: 300 },
                scrollHeight: { configurable: true, value: 800 },
            });
            Object.defineProperties(lastPost, {
                offsetTop: { configurable: true, value: 760 },
                offsetHeight: { configurable: true, value: 40 },
            });

            expect(renderedPosts).toHaveLength(overflowingPosts.length);
            expect(postContainer.scrollHeight).toBeGreaterThan(postContainer.clientHeight);
            expect(host.classList).toContain('min-h-0');
            expect(host.classList).toContain('flex-1');
            expect(wrapper.classList).toContain('min-h-0');
            expect(wrapper.classList).toContain('flex-1');
            expect(postContainer.classList).toContain('min-h-0');
            expect(postContainer.classList).toContain('flex-1');
            expect(postContainer.classList).toContain('overflow-y-auto');

            postContainer.scrollTop = postContainer.scrollHeight - postContainer.clientHeight;

            expect(lastPost.offsetTop + lastPost.offsetHeight).toBeLessThanOrEqual(postContainer.scrollTop + postContainer.clientHeight);
            expect(postContainer.contains(heading)).toBe(false);
            expect(wrapper.contains(heading)).toBe(true);
            expect(wrapper.scrollTop).toBe(0);
        });
    });
});
