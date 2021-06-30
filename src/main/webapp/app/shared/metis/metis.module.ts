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
import { PostHeaderComponent } from 'app/shared/metis/post/post-header/post-header.component';
import { AnswerPostHeaderComponent } from 'app/shared/metis/answer-post/answer-post-header/answer-post-header.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisConfirmIconModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    declarations: [
        PostingsThreadComponent,
        PostComponent,
        PostHeaderComponent,
        PostVotesComponent,
        AnswerPostComponent,
        AnswerPostHeaderComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
    ],
    exports: [
        PostingsThreadComponent,
        PostComponent,
        PostHeaderComponent,
        PostVotesComponent,
        AnswerPostComponent,
        AnswerPostHeaderComponent,
        PostingsButtonComponent,
        PostingsMarkdownEditorComponent,
    ],
})
export class MetisModule {}
