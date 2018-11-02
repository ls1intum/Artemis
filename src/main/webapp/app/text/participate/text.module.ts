import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { textRoute } from './text.route';
import { TextComponent } from 'app/text/participate/text.component';

const ENTITY_STATES = [...textRoute];

@NgModule({
    declarations: [TextComponent],
    entryComponents: [TextComponent],
    imports: [RouterModule.forChild(ENTITY_STATES)]
})
export class ArTEMiSTextModule {}
