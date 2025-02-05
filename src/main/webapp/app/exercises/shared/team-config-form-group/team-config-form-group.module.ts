import { NgModule } from '@angular/core';

import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, TeamConfigFormGroupComponent],
    exports: [TeamConfigFormGroupComponent],
})
export class ArtemisTeamConfigFormGroupModule {}
