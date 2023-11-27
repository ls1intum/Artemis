import { Component, ViewChild } from '@angular/core';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';

@Component({
    selector: 'jhi-iris-global-settings-update',
    templateUrl: './iris-global-settings-update.component.html',
})
export class IrisGlobalSettingsUpdateComponent implements ComponentCanDeactivate {
    @ViewChild(IrisSettingsUpdateComponent)
    settingsUpdateComponent?: IrisSettingsUpdateComponent;

    GLOBAL = IrisSettingsType.GLOBAL;

    canDeactivate(): boolean {
        return this.settingsUpdateComponent?.canDeactivate() ?? true;
    }

    get canDeactivateWarning(): string | undefined {
        return this.settingsUpdateComponent?.canDeactivateWarning;
    }
}
