import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { PostTagSelectorComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { OverlayModule } from '@angular/cdk/overlay';
import { CommonModule } from '@angular/common';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { PostingContentComponent } from './posting-content/posting-content.components';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';
import { ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { MatChipsModule } from '@angular/material/chips';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDialogModule } from '@angular/material/dialog';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { MessageReplyInlineInputComponent } from 'app/shared/metis/message/message-reply-inline-input/message-reply-inline-input.component';
import { EnlargeSlideImageComponent } from 'app/shared/metis/posting-content/enlarge-slide-image/enlarge-slide-image.component';
import { LinkPreviewModule } from 'app/shared/link-preview/link-preview.module';
import { LinkPreviewComponent } from 'app/shared/link-preview/components/link-preview/link-preview.component';
import { LinkPreviewContainerComponent } from 'app/shared/link-preview/components/link-preview-container/link-preview-container.component';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisConfirmIconModule,
        ArtemisMarkdownModule,
        ArtemisMarkdownEditorModule,
        ReactiveFormsModule,
        FormsModule,
        ArtemisSharedComponentModule,
        PickerModule,
        EmojiModule,
        OverlayModule,
        CommonModule,
        FontAwesomeModule,
        MatChipsModule,
        MatAutocompleteModule,
        MatSelectModule,
        MatFormFieldModule,
        MatDialogModule,
        LinkPreviewModule,
    ],
    declarations: [
        PostingThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        PostTagSelectorComponent,
        PostFooterComponent,
        AnswerPostCreateEditModalComponent,
        PostingButtonComponent,
        PostingMarkdownEditorComponent,
        PostComponent,
        AnswerPostComponent,
        PostingContentComponent,
        PostingContentPartComponent,
        LinkPreviewComponent,
        LinkPreviewContainerComponent,
        EnlargeSlideImageComponent,
        PostReactionsBarComponent,
        AnswerPostReactionsBarComponent,
        MessageInlineInputComponent,
        MessageReplyInlineInputComponent,
        HtmlForPostingMarkdownPipe,
        ReactingUsersOnPostingPipe,
        EmojiComponent,
        EmojiPickerComponent,
    ],
    exports: [
        PostingThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        PostTagSelectorComponent,
        AnswerPostCreateEditModalComponent,
        PostFooterComponent,
        PostingButtonComponent,
        PostingMarkdownEditorComponent,
        PostComponent,
        AnswerPostComponent,
        PostingContentComponent,
        PostingContentPartComponent,
        LinkPreviewComponent,
        LinkPreviewContainerComponent,
        EnlargeSlideImageComponent,
        PostReactionsBarComponent,
        AnswerPostReactionsBarComponent,
        MessageInlineInputComponent,
        MessageReplyInlineInputComponent,
        HtmlForPostingMarkdownPipe,
        EmojiComponent,
        EmojiPickerComponent,
    ],
    providers: [MetisConversationService],
})
export class MetisModule {}
