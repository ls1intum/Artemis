import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { TeamConfigFormGroupComponent } from 'app/components/exercise/team-config-form-group/team-config-form-group.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModePickerModule } from 'app/components/exercise/mode-picker/mode-picker.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisModePickerModule],
    declarations: [TeamConfigFormGroupComponent],
    exports: [TeamConfigFormGroupComponent],
})
export class ArtemisTeamConfigFormGroupModule {}
