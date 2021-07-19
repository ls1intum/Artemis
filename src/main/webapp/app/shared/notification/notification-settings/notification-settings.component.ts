import { Component, EventEmitter, OnInit, Output } from '@angular/core';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['./notification-settings.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    @Output()
    onClose: EventEmitter<boolean> = new EventEmitter();

    closeSettings() {
        this.onClose.emit(true);
    }

    ngOnInit(): void {}
}
