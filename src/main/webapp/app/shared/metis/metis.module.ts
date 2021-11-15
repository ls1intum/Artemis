import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { AnswerPostHeaderComponent } from 'app/shared/metis/postings-header/answer-post-header/answer-post-header.component';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { PostTagSelectorComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TagInputModule } from 'ngx-chips';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { OverlayModule } from '@angular/cdk/overlay';
import { CommonModule } from '@angular/common';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { PostingContentComponent } from './posting-content/posting-content.components';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { PostingContentMarkdownLinebreakPipe } from '../pipes/posting-content-markdown-linebreak.pipe';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { CourseDiscussionComponent } from 'app/overview/course-discussion/course-discussion.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisConfirmIconModule,
        ArtemisMarkdownModule,
        ArtemisMarkdownEditorModule,
        ArtemisCoursesRoutingModule,
        ReactiveFormsModule,
        FormsModule,
        TagInputModule,
        ArtemisSharedComponentModule,
        PickerModule,
        EmojiModule,
        OverlayModule,
        CommonModule,
        FontAwesomeModule,
    ],
    declarations: [
        PostingsThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        PostTagSelectorComponent,
        PostFooterComponent,
        AnswerPostCreateEditModalComponent,
        AnswerPostFooterComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
        PostComponent,
        AnswerPostComponent,
        PostingContentComponent,
        PostingContentPartComponent,
        PostReactionsBarComponent,
        AnswerPostReactionsBarComponent,
        PostingContentMarkdownLinebreakPipe,
    ],
    exports: [
        PostingsThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        PostTagSelectorComponent,
        AnswerPostCreateEditModalComponent,
        PostFooterComponent,
        AnswerPostFooterComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
        PostComponent,
        AnswerPostComponent,
        PostingContentComponent,
        PostingContentPartComponent,
        PostReactionsBarComponent,
        AnswerPostReactionsBarComponent,
        PostingContentMarkdownLinebreakPipe,
    ],
})
export class MetisModule {}
