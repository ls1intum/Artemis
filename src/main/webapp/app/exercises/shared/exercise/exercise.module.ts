import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormsModule],
})
export class ArtemisExerciseModule {}
