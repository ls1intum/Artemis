import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';

@Component({
    selector: 'jhi-notification-mgmt-delete-dialog',
    templateUrl: './notification-management-delete-dialog.component.html',
})
export class NotificationMgmtDeleteDialogComponent {
    notification: SystemNotification;

    constructor(private systemNotificationService: SystemNotificationService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    /**
     * Closes the dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Calls SystemNotificationService to delete notification and closes the dialog
     * @param id of the notification
     */
    confirmDelete(id: number) {
        this.systemNotificationService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'notificationListModification',
                content: 'Deleted a system notification',
            });
            this.activeModal.dismiss(true);
        });
    }
}
