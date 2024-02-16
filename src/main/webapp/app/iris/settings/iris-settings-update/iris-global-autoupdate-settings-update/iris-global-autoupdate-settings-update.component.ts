import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IrisGlobalSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';

@Component({
    selector: 'jhi-iris-global-autoupdate-settings-update',
    templateUrl: './iris-global-autoupdate-settings-update.component.html',
})
export class IrisGlobalAutoupdateSettingsUpdateComponent {
    @Input()
    irisSettings?: IrisGlobalSettings;

    @Output()
    onChanges = new EventEmitter<IrisSubSettings>();
}
