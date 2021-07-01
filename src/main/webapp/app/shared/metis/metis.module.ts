import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { PostVotesComponent } from 'app/shared/metis/post/post-votes/post-votes.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { AnswerPostHeaderComponent } from 'app/shared/metis/postings-header/answer-post-header/answer-post-header.component';
import { PostingsFooterComponent } from 'app/shared/metis/postings-footer/postings-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisConfirmIconModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    declarations: [
        PostingsThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        AnswerPostCreateEditModalComponent,
        PostingsFooterComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
        PostComponent,
        PostVotesComponent,
        AnswerPostComponent,
    ],
    exports: [
        PostingsThreadComponent,
        PostHeaderComponent,
        AnswerPostHeaderComponent,
        PostCreateEditModalComponent,
        AnswerPostCreateEditModalComponent,
        PostingsFooterComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
        PostComponent,
        PostVotesComponent,
        AnswerPostComponent,
    ],
})
export class MetisModule {}
