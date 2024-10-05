import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { Organization } from 'app/entities/organization.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { COMMA, ENTER, TAB } from '@angular/cdk/keycodes';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { MatChipInputEvent } from '@angular/material/chips';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { CourseAdminService } from 'app/course/manage/course-admin.service';

@Component({
    selector: 'jhi-user-management-update',
    templateUrl: './user-management-update.component.html',
    styleUrls: ['./user-management-update.component.scss'],
})
export class UserManagementUpdateComponent implements OnInit {
    private languageHelper = inject(JhiLanguageHelper);
    private userService = inject(AdminUserService);
    private courseManagementService = inject(CourseManagementService);
    private courseAdminService = inject(CourseAdminService);
    private route = inject(ActivatedRoute);
    private organizationService = inject(OrganizationManagementService);
    private modalService = inject(NgbModal);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private alertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private fb = inject(FormBuilder);

    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    readonly EMAIL_MIN_LENGTH = 5;
    readonly EMAIL_MAX_LENGTH = 100;
    readonly REGISTRATION_NUMBER_MAX_LENGTH = 20;

    user: User;
    languages: string[];
    authorities: string[];
    isSaving: boolean;
    allGroups: string[];
    filteredGroups: Observable<string[]>;

    separatorKeysCodes = [ENTER, COMMA, TAB];

    groupCtrl = new FormControl();

    // Icons
    faTimes = faTimes;
    faBan = faBan;
    faSave = faSave;
    editForm: FormGroup;

    private oldLogin?: string;
    private isJenkins: boolean;

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
                this.oldLogin = this.user.login;
                this.organizationService.getOrganizationsByUser(this.user.id!).subscribe((organizations) => {
                    this.user.organizations = organizations;
                });
            }
        });
        this.courseAdminService.getAllGroupsForAllCourses().subscribe((groups) => {
            this.allGroups = [];
            if (groups.body) {
                groups.body.forEach((group) => {
                    this.allGroups.push(group);
                });
            }
            this.filteredGroups = this.groupCtrl.valueChanges.pipe(
                startWith(null),
                map((value) => (value ? this.filter(value) : this.allGroups.slice())),
            );
        });
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.isJenkins = profileInfo.activeProfiles.includes('jenkins');
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
        this.initializeForm();
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     * Returns to the detail page if there is no previous state, and we edited an existing user
     * Returns to the overview page if there is no previous state, and we created a new user
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
            this.userService.update(this.user).subscribe({
                next: () => {
                    if (this.isJenkins && this.user.login !== this.oldLogin && !this.user.password) {
                        this.alertService.addAlert({
                            type: AlertType.WARNING,
                            message: 'artemisApp.userManagement.jenkinsChange',
                            timeout: 0,
                            translationParams: { oldLogin: this.oldLogin, newLogin: this.user.login },
                        });
                    }
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.userService.create(this.user).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
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
        this.user.organizations = this.user.organizations!.filter((userOrganization) => userOrganization.id !== organization.id);
    }

    /**
     * Adds a group to the user
     * @param user to add the group to
     * @param event chip input event
     */
    onGroupAdd(user: User, event: MatChipInputEvent) {
        const groupString = (event.value || '').trim();
        this.addGroup(user, groupString);
        this.groupCtrl.setValue('');
        event.chipInput!.clear();
    }

    /**
     * Removes a group from the user
     * @param user to remove the group from
     * @param group to remove
     */
    onGroupRemove(user: User, group: string) {
        user.groups = user.groups?.filter((userGroup) => userGroup !== group);
    }

    /**
     * Adds the selected group from panel to the user
     * @param event autocomplete event
     */
    onSelected(event: MatAutocompleteSelectedEvent): void {
        const groupString = (event.option.viewValue || '').trim();
        this.addGroup(this.user, groupString);
        this.groupCtrl.setValue('');
    }

    private initializeForm() {
        if (this.editForm) {
            return;
        }
        this.editForm = this.fb.group({
            idInput: ['', []],
            loginInput: ['', [Validators.required, Validators.minLength(USERNAME_MIN_LENGTH), Validators.maxLength(USERNAME_MAX_LENGTH)]],
            firstNameInput: ['', [Validators.required, Validators.maxLength(USERNAME_MAX_LENGTH)]],
            lastNameInput: ['', [Validators.required, Validators.maxLength(USERNAME_MAX_LENGTH)]],
            passwordInput: ['', [Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
            emailInput: ['', [Validators.required, Validators.minLength(this.EMAIL_MIN_LENGTH), Validators.maxLength(this.EMAIL_MAX_LENGTH)]],
            registrationNumberInput: ['', [Validators.maxLength(this.REGISTRATION_NUMBER_MAX_LENGTH)]],
            activatedInput: ['', []],
            langKeyInput: ['', []],
            authorityInput: ['', []],
            internalInput: [{ value: this.user.internal, disabled: true }],
        });
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

    /**
     * Filter the groups based on the input value
     * @param value input value
     */
    private filter(value: string): string[] {
        const filterValue = value.toLowerCase();
        return this.allGroups.filter((group) => group.toLowerCase().includes(filterValue));
    }

    /**
     * Adds a group to the user if it is valid
     * @param user to add the group to
     * @param groupString group to add
     */
    private addGroup(user: User, groupString: string) {
        if (groupString && this.allGroups.includes(groupString) && !user.groups?.includes(groupString)) {
            if (!user.groups) {
                user.groups = [];
            }
            user.groups.push(groupString);
        }
    }
}
