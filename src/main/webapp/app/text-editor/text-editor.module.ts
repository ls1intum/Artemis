import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { textEditorRoute } from './text-editor.route';
import { TextEditorComponent } from './text-editor.component';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';

const ENTITY_STATES = [...textEditorRoute];

@NgModule({
    declarations: [TextEditorComponent],
    entryComponents: [TextEditorComponent],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule]
})
export class ArTEMiSTextModule {}
