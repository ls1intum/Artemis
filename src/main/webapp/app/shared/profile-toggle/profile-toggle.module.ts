import { NgModule } from '@angular/core';
import { ProfileToggleDirective } from './profile-toggle.directive';
import { ProfileToggleLinkDirective } from './profile-toggle-link.directive';
import { ProfileToggleHideDirective } from './profile-toggle-hide.directive';

@NgModule({
    declarations: [ProfileToggleDirective, ProfileToggleLinkDirective, ProfileToggleHideDirective],
    exports: [ProfileToggleDirective, ProfileToggleLinkDirective, ProfileToggleHideDirective],
})
export class ProfileToggleModule {}
