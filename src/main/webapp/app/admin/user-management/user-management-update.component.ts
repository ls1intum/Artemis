import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { Organization } from 'app/entities/organization.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-user-management-update',
    templateUrl: './user-management-update.component.html',
})
export class UserManagementUpdateComponent implements OnInit {
    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    user: User;
    languages: string[];
    authorities: string[];
    isSaving: boolean;

    // Icons
    faTimes = faTimes;
    faBan = faBan;
    faSave = faSave;

    constructor(
        private languageHelper: JhiLanguageHelper,
        private userService: UserService,
        private route: ActivatedRoute,
        private organizationService: OrganizationManagementService,
        private modalService: NgbModal,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    /**
     * Enable subscriptions to retrieve the user based on the activated route, all authorities and all languages on init
     */
    ngOnInit() {
        this.isSaving = false;

        // create a new user, and only overwrite it if we fetch a user to edit
        this.user = new User();
        this.route.parent!.data.subscribe(({ user }) => {
            if (user) {
                this.user = user.body ? user.body : user;
                this.organizationService.getOrganizationsByUser(this.user.id!).subscribe((organizations) => {
                    this.user.organizations = organizations;
                });
            }
        });
        this.authorities = [];
        this.userService.authorities().subscribe((authorities) => {
            this.authorities = authorities;
        });
        this.languages = this.languageHelper.getAll();
        // Empty array for new user
        if (!this.user.id) {
            this.user.groups = [];
        }
        // Set password to undefined. ==> If it still is undefined on save, it won't be changed for existing users. It will be random for new users
        this.user.password = undefined;
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     * Returns to the detail page if there is no previous state and we edited an existing user
     * Returns to the overview page if there is no previous state and we created a new user
     */
    previousState() {
        if (this.user.id) {
            this.navigationUtilService.navigateBack(['admin', 'user-management', this.user.login!.toString()]);
        } else {
            this.navigationUtilService.navigateBack(['admin', 'user-management']);
        }
    }

    /**
     * Update or create user in the user management component
     */
    save() {
        this.isSaving = true;
        if (this.user.id) {
            this.userService.update(this.user).subscribe(
                () => this.onSaveSuccess(),
                () => this.onSaveError(),
            );
        } else {
            this.userService.create(this.user).subscribe(
                () => this.onSaveSuccess(),
                () => this.onSaveError(),
            );
        }
    }

    /**
     * Set isSaving to false and navigate to previous page
     */
    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    /**
     * Set isSaving to false
     */
    private onSaveError() {
        this.isSaving = false;
    }

    shouldRandomizePassword(useRandomPassword: any) {
        if (useRandomPassword) {
            this.user.password = undefined;
        } else {
            this.user.password = '';
        }
    }

    /**
     * Opens the organizations modal used to select an organization to add
     */
    openOrganizationsModal() {
        const modalRef = this.modalService.open(OrganizationSelectorComponent, { size: 'xl', backdrop: 'static' });
        modalRef.componentInstance.organizations = this.user.organizations;
        modalRef.closed.subscribe((organization) => {
            if (organization !== undefined) {
                if (this.user.organizations === undefined) {
                    this.user.organizations = [];
                }
                this.user.organizations!.push(organization);
            }
        });
    }

    /**
     * Removes an organization from the user
     * @param organization to remove
     */
    removeOrganizationFromUser(organization: Organization) {
        this.user.organizations = this.user.organizations!.filter((o) => o.id !== organization.id);
    }
}
