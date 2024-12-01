import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SavedPostsComponent } from 'app/overview/course-conversations/saved-posts/saved-posts.component';
import { SavedPostService } from 'app/shared/metis/saved-post.service';
import { Posting, SavedPostStatus } from 'app/entities/metis/posting.model';
import { EMPTY, of, throwError } from 'rxjs';
import { MockComponent, MockDirective } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingSummaryComponent } from 'app/overview/course-conversations/posting-summary/posting-summary.component';

describe('SavedPostsComponent', () => {
    let component: SavedPostsComponent;
    let fixture: ComponentFixture<SavedPostsComponent>;
    let savedPostService: jest.Mocked<SavedPostService>;

    const mockPosting: Posting = {
        id: 1,
        content: 'Test Content',
    };

    const mockPostings: Posting[] = [mockPosting, { id: 2, content: 'Test Content 2' }];

    beforeEach(async () => {
        const mockSavedPostService = {
            fetchSavedPosts: jest.fn(),
            convertPostingToCorrespondingType: jest.fn((post) => post),
            changeSavedPostStatus: jest.fn(),
        };

        await TestBed.configureTestingModule({
            declarations: [SavedPostsComponent, MockDirective(TranslateDirective), MockComponent(FaIconComponent), MockComponent(PostingSummaryComponent)],
            providers: [{ provide: SavedPostService, useValue: mockSavedPostService }],
        }).compileComponents();

        savedPostService = TestBed.inject(SavedPostService) as jest.Mocked<SavedPostService>;
        fixture = TestBed.createComponent(SavedPostsComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Initialization', () => {
        it('should fetch saved posts successfully', fakeAsync(() => {
            const courseId = 123;
            const status = SavedPostStatus.PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: mockPostings })));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(savedPostService.fetchSavedPosts).toHaveBeenCalledWith(courseId, status);
            expect(component['posts']).toEqual(mockPostings);
            expect(component['hiddenPosts']).toEqual([]);
        }));

        it('should handle empty response', fakeAsync(() => {
            const courseId = 123;
            const status = SavedPostStatus.PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: [] })));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(component['posts']).toEqual([]);
        }));

        it('should handle error response', fakeAsync(() => {
            const courseId = 123;
            const status = SavedPostStatus.PROGRESS;
            savedPostService.fetchSavedPosts.mockReturnValue(throwError(() => new Error('Test error')));

            fixture.componentRef.setInput('courseId', courseId);
            fixture.componentRef.setInput('savedPostStatus', status);
            fixture.detectChanges();

            expect(component['posts']).toEqual([]);
        }));
    });

    describe('Change post status', () => {
        it('should update post status and add to hidden posts', fakeAsync(() => {
            const newStatus = SavedPostStatus.ARCHIVED;
            savedPostService.changeSavedPostStatus.mockReturnValue(of({}));

            component['changeSavedPostStatus'](mockPosting, newStatus);

            expect(savedPostService.changeSavedPostStatus).toHaveBeenCalledWith(mockPosting, SavedPostStatus.ARCHIVED);
            expect(component['hiddenPosts']).toContain(mockPosting.id);
        }));
    });

    describe('Navigation', () => {
        it('should emit post when navigating', () => {
            const emitSpy = jest.spyOn(component.onNavigateToPost, 'emit');

            component['onTriggerNavigateToPost'](mockPosting);

            expect(emitSpy).toHaveBeenCalledWith(mockPosting);
        });
    });

    describe('Template interaction', () => {
        beforeEach(async () => {
            const status = SavedPostStatus.PROGRESS;
            fixture.componentRef.setInput('savedPostStatus', status);
            savedPostService.fetchSavedPosts.mockReturnValue(EMPTY);
        });

        it('should show empty notice when no posts are available', () => {
            component['posts'] = [];
            fixture.detectChanges();

            const emptyNotice = fixture.nativeElement.querySelector('.saved-posts-empty-notice');
            expect(emptyNotice).toBeTruthy();
        });

        it('should not show empty notice when posts are available', fakeAsync(() => {
            const courseId = 123;
            savedPostService.fetchSavedPosts.mockReturnValue(of(new HttpResponse({ body: mockPostings })));
            fixture.componentRef.setInput('courseId', courseId);
            fixture.detectChanges();
            tick();
            fixture.detectChanges();

            const emptyNotice = fixture.nativeElement.querySelector('.saved-posts-empty-notice');
            expect(emptyNotice).toBeFalsy();
        }));

        it('should show delete post notice when archived is selected', fakeAsync(() => {
            fixture.componentRef.setInput('savedPostStatus', SavedPostStatus.ARCHIVED);
            fixture.detectChanges();
            tick();
            fixture.detectChanges();

            const optionsElement = fixture.nativeElement.querySelector('.saved-posts-delete-notice');
            expect(component['isShowDeleteNotice']).toBeTruthy();
            expect(optionsElement).toBeTruthy();
        }));
    });
});
