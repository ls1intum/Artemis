import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { TeamScopePickerComponent } from 'app/components/exercise/team-scope-picker/team-scope-picker.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [TeamScopePickerComponent],
    exports: [TeamScopePickerComponent],
})
export class ArtemisTeamScopePickerModule {}
