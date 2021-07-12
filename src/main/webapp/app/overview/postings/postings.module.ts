import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { PostingsComponent } from 'app/overview/postings/postings.component';
import { PostRowComponent } from 'app/overview/postings/post-row/post-row.component';
import { PostComponent } from 'app/overview/postings/post/post.component';
import { AnswerPostComponent } from 'app/overview/postings/answer-post/answer-post.component';
import { PostVotesComponent } from 'app/overview/postings/post-votes/post-votes.component';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { PostingsButtonComponent } from 'app/overview/postings/postings-button/postings-button.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: PostingsComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSidePanelModule, ArtemisConfirmIconModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    declarations: [PostingsComponent, PostRowComponent, PostComponent, AnswerPostComponent, PostVotesComponent, PostingsButtonComponent],
    exports: [PostingsComponent, PostingsButtonComponent],
})
export class ArtemisPostingsModule {}
