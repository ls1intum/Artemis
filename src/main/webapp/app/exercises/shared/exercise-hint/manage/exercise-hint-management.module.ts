import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { textHintRoute } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.route';
import { TextHintDetailComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-detail.component';
import { TextHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { TextHintComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const ENTITY_STATES = [...textHintRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormsModule, ReactiveFormsModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    declarations: [TextHintComponent, TextHintDetailComponent, TextHintUpdateComponent],
})
export class ArtemisTextHintManagementModule {}
