import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { textRoute } from './text.route';
import { TextComponent } from './text.component';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';

const ENTITY_STATES = [...textRoute];

@NgModule({
    declarations: [TextComponent],
    entryComponents: [TextComponent],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule]
})
export class ArTEMiSTextModule {}
