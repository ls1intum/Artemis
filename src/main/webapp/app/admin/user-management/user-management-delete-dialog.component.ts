import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { User, UserService } from 'app/core';

@Component({
    selector: 'jhi-user-management-delete-dialog',
    templateUrl: './user-management-delete-dialog.component.html',
})
export class UserManagementDeleteDialogComponent {
    user: User;

    constructor(private userService: UserService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    /**
     * Cancels and closes the user management delete dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Confirms and deletes the user with the given login string
     * @param login string of the user that should be deleted
     */
    confirmDelete(login: string) {
        this.userService.delete(login).subscribe(response => {
            this.eventManager.broadcast({
                name: 'userListModification',
                content: 'Deleted a user',
            });
            this.activeModal.dismiss(true);
        });
    }
}
