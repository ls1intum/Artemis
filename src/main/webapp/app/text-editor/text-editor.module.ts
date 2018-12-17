import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { textEditorRoute } from './text-editor.route';
import { TextEditorComponent } from './text-editor.component';
import { TextEditorScoreCardComponent } from './text-editor-score-card/text-editor-score-card.component';
import { TextSharedModule } from 'app/text-shared/text-shared.module';

const ENTITY_STATES = [...textEditorRoute];

@NgModule({
    declarations: [TextEditorComponent, TextEditorScoreCardComponent],
    entryComponents: [TextEditorComponent],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule, TextSharedModule]
})
export class ArTEMiSTextModule {}
