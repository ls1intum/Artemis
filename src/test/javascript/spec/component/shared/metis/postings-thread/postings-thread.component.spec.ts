import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent, MockInstance } from 'ng-mocks';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { post } from '../../../../helpers/sample/metis-sample-data';
import { getElement } from '../../../../helpers/utils/general.utils';
import { signal } from '@angular/core';
import { PostingReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';

describe('PostingThreadComponent', () => {
    let component: PostingThreadComponent;
    let fixture: ComponentFixture<PostingThreadComponent>;

    beforeEach(() => {
        // Workaround for mocked components with viewChild: https://github.com/help-me-mom/ng-mocks/issues/8634
        MockInstance(PostComponent, 'postFooterComponent', signal(undefined));
        MockInstance(PostComponent, 'reactionsBarComponent', signal({} as unknown as PostingReactionsBarComponent<Post>));

        return TestBed.configureTestingModule({
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostingThreadComponent,
                TranslatePipeMock,
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(FaIconComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingThreadComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should contain a post', () => {
        component.post = post;

        fixture.detectChanges();

        const postComponent = getElement(fixture.debugElement, 'jhi-post');
        expect(postComponent).not.toBeNull();
    });

    it('should emit onNavigateToPost when onTriggerNavigateToPost is called', () => {
        const testPosting = { id: 999, content: 'Test content' } as Posting;
        const onNavigateToPostSpy = jest.spyOn(component.onNavigateToPost, 'emit');

        component.onTriggerNavigateToPost(testPosting);

        expect(onNavigateToPostSpy).toHaveBeenCalledOnce();
        expect(onNavigateToPostSpy).toHaveBeenCalledWith(testPosting);
    });

    it('should handle null or undefined posting correctly', () => {
        const onNavigateToPostSpy = jest.spyOn(component.onNavigateToPost, 'emit');

        component.onTriggerNavigateToPost(null as any);
        expect(onNavigateToPostSpy).toHaveBeenCalledWith(null);

        component.onTriggerNavigateToPost(undefined as any);
        expect(onNavigateToPostSpy).toHaveBeenCalledWith(undefined);
    });
});
