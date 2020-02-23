import { NgModule } from '@angular/core';

import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisModePickerModule],
    declarations: [TeamConfigFormGroupComponent],
    exports: [TeamConfigFormGroupComponent],
})
export class ArtemisTeamConfigFormGroupModule {}
