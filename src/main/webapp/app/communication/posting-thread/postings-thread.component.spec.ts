import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { PostService } from 'app/communication/service/post.service';
import { MockPostService } from 'test/helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { MockAnswerPostService } from 'test/helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MockComponent, MockInstance } from 'ng-mocks';
import { PostComponent } from 'app/communication/post/post.component';
import { AnswerPostComponent } from 'app/communication/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { post } from 'test/helpers/sample/metis-sample-data';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { signal } from '@angular/core';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DialogService } from 'primeng/dynamicdialog';

describe('PostingThreadComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PostingThreadComponent>;

    beforeEach(() => {
        // Workaround for mocked components with viewChild: https://github.com/help-me-mom/ng-mocks/issues/8634
        MockInstance(PostComponent, 'postFooterComponent', signal(undefined));
        MockInstance(PostComponent, 'reactionsBarComponent', signal({} as unknown as PostingReactionsBarComponent<Post>));

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
            imports: [
                PostingThreadComponent,
                TranslatePipeMock,
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(FaIconComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        });
        TestBed.overrideComponent(PostingThreadComponent, {
            remove: { imports: [PostComponent] },
            add: { imports: [MockComponent(PostComponent)] },
        });
        fixture = TestBed.createComponent(PostingThreadComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should contain a post', () => {
        fixture.componentRef.setInput('post', post);
        fixture.componentRef.setInput('showAnswers', true);

        fixture.changeDetectorRef.detectChanges();

        const postComponent = getElement(fixture.debugElement, 'jhi-post');
        expect(postComponent).not.toBeNull();
    });
});
