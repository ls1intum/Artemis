import { Component } from '@angular/core';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';

@Component({
    selector: 'jhi-iris-global-settings-update',
    templateUrl: './iris-global-settings-update.component.html',
})
export class IrisGlobalSettingsUpdateComponent {
    GLOBAL = IrisSettingsType.GLOBAL;
}
