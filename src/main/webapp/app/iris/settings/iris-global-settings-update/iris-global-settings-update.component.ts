import { Component } from '@angular/core';
import { IrisSettingsType } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';

@Component({
    selector: 'jhi-iris-global-settings-update',
    templateUrl: './iris-global-settings-update.component.html',
})
export class IrisGlobalSettingsUpdateComponent {
    GLOBAL = IrisSettingsType.GLOBAL;
}
