import { NgModule } from '@angular/core';
import { ProfileToggleDirective } from './profile-toggle.directive';
import { ProfileToggleLinkDirective } from './profile-toggle-link.directive';

@NgModule({
    declarations: [ProfileToggleDirective, ProfileToggleLinkDirective],
    exports: [ProfileToggleDirective, ProfileToggleLinkDirective],
})
export class ProfileToggleModule {}
