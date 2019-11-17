import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { textEditorRoute } from './text-editor.route';
import { TextEditorComponent } from './text-editor.component';
import { TextEditorScoreCardComponent } from './text-editor-score-card/text-editor-score-card.component';
import { ArtemisComplaintsModule } from 'app/complaints';
import { TextResultComponent } from './text-result/text-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MomentModule } from 'ngx-moment';

const ENTITY_STATES = [...textEditorRoute];

@NgModule({
    declarations: [TextEditorComponent, TextEditorScoreCardComponent, TextResultComponent],
    entryComponents: [TextEditorComponent],
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisResultModule, ArtemisComplaintsModule, ArtemisSharedComponentModule, MomentModule],
})
export class ArtemisTextModule {}
