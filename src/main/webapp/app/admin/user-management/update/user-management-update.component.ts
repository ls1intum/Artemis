import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/admin/organization-selector/organization-selector.component';
import { Organization } from 'app/admin/organization-management/organization.model';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiCheckboxComponent } from 'app/shared-ui/tum-ui/checkbox/tum-ui-checkbox.component';
import { TumUiSelectComponent } from 'app/shared-ui/tum-ui/select/tum-ui-select.component';
import {
    TumUiAutoCompleteCompleteEvent,
    TumUiAutoCompleteComponent,
    TumUiAutoCompleteSelectEvent,
    TumUiAutoCompleteUnselectEvent,
} from 'app/shared-ui/tum-ui/autocomplete/tum-ui-autocomplete.component';
import { TumUiChipComponent } from 'app/shared-ui/tum-ui/chip/tum-ui-chip.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, PROFILE_JENKINS, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { CourseAdminService } from 'app/course/manage/services/course-admin.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FindLanguageFromKeyPipe } from 'app/foundation/language/find-language-from-key.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/foundation/constants/authority.constants';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/**
 * Component for creating and updating users in the admin user management.
 * Provides a form with validation for user properties, groups, and organizations.
 */
@Component({
    selector: 'jhi-user-management-update',
    templateUrl: './user-management-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        TumUiTooltipDirective,
        HelpIconComponent,
        TumUiInputDirective,
        TumUiCheckboxComponent,
        TumUiSelectComponent,
        TumUiAutoCompleteComponent,
        TumUiChipComponent,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiDialogComponent,
        OrganizationSelectorComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        AdminTitleBarTitleDirective,
    ],
})
export class UserManagementUpdateComponent implements OnInit {
    private readonly languageHelper = inject(JhiLanguageHelper);
    private readonly userService = inject(AdminUserService);
    private readonly courseAdminService = inject(CourseAdminService);
    private readonly route = inject(ActivatedRoute);
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);
    private readonly fb = inject(FormBuilder);
    private readonly accountService = inject(AccountService);

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;

    /** Controls visibility of the declarative organization-selector dialog. */
    readonly orgSelectorVisible = signal(false);

    private readonly findLanguageFromKeyPipe = new FindLanguageFromKeyPipe();

    /** Validation constants */
    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;
    readonly EMAIL_MIN_LENGTH = 5;
    readonly EMAIL_MAX_LENGTH = 100;
    readonly REGISTRATION_NUMBER_MAX_LENGTH = 20;

    /** The user being edited. Signal so async mutations (route resolver data, organizations fetched via HTTP) render under zoneless. */
    readonly user = signal<User>(undefined!);

    /** Available languages for selection */
    readonly languages = signal<string[]>(undefined!);

    /** Language options ({ label, value }) derived for the PrimeNG select. */
    readonly languageOptions = computed(() => (this.languages() ?? []).map((language) => ({ label: this.findLanguageFromKeyPipe.transform(language), value: language })));

    /** Whether a random password should be generated (new users) or the old password kept (existing users). */
    readonly useRandomPassword = signal(true);

    /** Available authorities for selection */
    readonly authorities = signal<string[]>([]);

    /** Sorted authorities by role hierarchy (super admin > admin > instructor > editor > tutor) */
    readonly sortedAuthorities = computed(() => {
        const roleOrder: Record<string, number> = {
            ROLE_SUPER_ADMIN: 0,
            ROLE_ADMIN: 1,
            ROLE_INSTRUCTOR: 2,
            ROLE_EDITOR: 3,
            ROLE_TA: 4,
            ROLE_USER: 5,
        };
        return [...this.authorities()].sort((a, b) => {
            const orderA = roleOrder[a] ?? 999;
            const orderB = roleOrder[b] ?? 999;
            return orderA - orderB;
        });
    });

    /** Whether the form is currently being submitted */
    readonly isSaving = signal(false);

    /** All available groups for autocomplete */
    allGroups: string[] = [];

    readonly groupSuggestions = signal<string[]>([]);

    /** Authority to translation key mapping */
    private readonly authorityTranslationKeys: Record<string, string> = {
        ROLE_SUPER_ADMIN: 'artemisApp.userManagement.roles.superAdmin',
        ROLE_ADMIN: 'artemisApp.userManagement.roles.admin',
        ROLE_INSTRUCTOR: 'artemisApp.userManagement.roles.instructor',
        ROLE_EDITOR: 'artemisApp.userManagement.roles.editor',
        ROLE_TA: 'artemisApp.userManagement.roles.tutor',
        ROLE_USER: 'artemisApp.userManagement.roles.user',
    };

    /** The reactive form for editing user properties */
    editForm!: FormGroup; // initialized in ngOnInit() via initializeForm()

    /** Original login for detecting changes */
    private oldLogin?: string;

    /** Whether Jenkins profile is active */
    private isJenkins = false;

    /**
     * Initializes the component by loading user data, authorities, languages, and groups.
     */
    ngOnInit(): void {
        // create a new user, and only overwrite it if we fetch a user to edit
        this.user.set(new User());
        this.route.parent!.data.subscribe(({ user }) => {
            if (user) {
                this.user.set(user.body ? user.body : user);
                this.oldLogin = this.user().login;
                this.organizationService.getOrganizationsByUser(this.user().id!).subscribe((organizations) => {
                    // Rebuild the user reference so the async organization update renders under zoneless.
                    this.user.update((currentUser) => cloneWith(currentUser, { organizations }));
                });
            }
        });
        this.courseAdminService.getAllGroupsForAllCourses().subscribe((groups) => {
            this.allGroups = [];
            if (groups.body) {
                groups.body.forEach((group) => {
                    if (group != undefined) {
                        this.allGroups.push(group);
                    }
                });
            }
            this.groupSuggestions.set(this.availableGroups());
        });
        this.isJenkins = this.profileService.isProfileActive(PROFILE_JENKINS);
        this.userService.authorities().subscribe((authorities) => {
            this.authorities.set(
                this.accountService.isSuperAdmin() ? authorities : authorities.filter((authority) => authority !== Authority.SUPER_ADMIN && authority !== Authority.ADMIN),
            );
        });
        this.languages.set(this.languageHelper.getAll());
        // Empty array for new user
        if (!this.user().id) {
            this.user().groups = [];
        }
        // Set password to undefined. ==> If it still is undefined on save, it won't be changed for existing users. It will be random for new users
        this.user().password = undefined;
        this.initializeForm();
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     * Returns to the detail page if there is no previous state, and we edited an existing user
     * Returns to the overview page if there is no previous state, and we created a new user
     */
    previousState() {
        if (this.user().id) {
            this.navigationUtilService.navigateBack(['admin', 'user-management', this.user().login!.toString()]);
        } else {
            this.navigationUtilService.navigateBack(['admin', 'user-management']);
        }
    }

    /**
     * Saves the user (creates new or updates existing).
     * Shows a warning for Jenkins users when login changes.
     */
    save(): void {
        this.isSaving.set(true);
        // temporarily store the user groups and organizations in variables, because they are not part of the edit form
        const userGroups = this.user().groups;
        const userOrganizations = this.user().organizations;
        const updatedUser: User = this.editForm.getRawValue();
        updatedUser.groups = userGroups;
        updatedUser.organizations = userOrganizations;
        this.user.set(updatedUser);
        if (updatedUser.id) {
            this.userService.update(updatedUser).subscribe({
                next: () => {
                    if (this.isJenkins && updatedUser.login !== this.oldLogin && !updatedUser.password) {
                        this.alertService.addAlert({
                            type: AlertType.WARNING,
                            message: 'artemisApp.userManagement.jenkinsChange',
                            timeout: 0,
                            translationParams: { oldLogin: this.oldLogin, newLogin: updatedUser.login },
                        });
                    }
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.userService.create(updatedUser).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    shouldRandomizePassword(useRandomPassword: boolean) {
        this.useRandomPassword.set(useRandomPassword);
        this.user().password = useRandomPassword ? undefined : '';
    }

    /**
     * Opens the organizations modal used to select an organization to add
     */
    openOrganizationsModal() {
        this.orgSelectorVisible.set(true);
    }

    /**
     * Adds the organization chosen in the selector dialog to the user.
     * @param organization the organization selected in the dialog
     */
    onOrgSelected(organization: Organization) {
        // Rebuild the user reference (new organizations array) so the dialog result renders under zoneless.
        this.user.update((currentUser) => cloneWith(currentUser, { organizations: [...(currentUser.organizations ?? []), organization] }));
    }

    /**
     * Removes an organization from the user
     * @param organization to remove
     */
    removeOrganizationFromUser(organization: Organization) {
        // Rebuild the user reference (new organizations array) so the updated list renders under zoneless.
        this.user.update((currentUser) =>
            cloneWith(currentUser, { organizations: currentUser.organizations!.filter((userOrganization) => userOrganization.id !== organization.id) }),
        );
    }

    /** Filters the group suggestions shown in the autocomplete dropdown based on the typed query. */
    filterGroups(event: TumUiAutoCompleteCompleteEvent): void {
        const query = (event.query ?? '').trim();
        this.groupSuggestions.set(query ? this.filter(query) : this.availableGroups());
    }

    onGroupSelect(event: TumUiAutoCompleteSelectEvent): void {
        // The group autocomplete operates over string suggestions, so the selected value is a string.
        const groupString = (typeof event.value === 'string' ? event.value : '').trim();
        this.addGroup(this.user(), groupString);
    }

    /**
     * Adds the group typed into the autocomplete when the user presses Enter. Cancels the key first so it does not
     * ALSO submit the surrounding `(ngSubmit)="save()"` edit form (which would save and navigate away mid-edit).
     */
    onGroupAdd(user: User, event: Event): void {
        event.preventDefault();
        event.stopPropagation();
        const input = event.target as HTMLInputElement;
        this.addGroup(user, (input.value || '').trim());
        // Reset the autocomplete through its own input handler (state-aware), not just the raw value, so its query
        // signal and suggestion panel clear too — otherwise the typed text and stale panel linger after Enter.
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    onGroupUnselect(event: TumUiAutoCompleteUnselectEvent): void {
        // The group autocomplete operates over string suggestions, so the removed value is a string.
        const group = typeof event.value === 'string' ? event.value : '';
        this.removeGroup(this.user(), group);
    }

    removeGroup(user: User, group: string) {
        user.groups = user.groups?.filter((userGroup) => userGroup !== group);
        this.commitUser(user);
    }

    private availableGroups(): string[] {
        const assigned = this.user()?.groups ?? [];
        return (this.allGroups ?? []).filter((group) => group != undefined && !assigned.includes(group));
    }

    private initializeForm() {
        if (this.editForm) {
            return;
        }
        this.editForm = this.fb.group({
            id: ['', []],
            login: ['', [Validators.required, Validators.minLength(USERNAME_MIN_LENGTH), Validators.maxLength(USERNAME_MAX_LENGTH)]],
            firstName: ['', [Validators.required, Validators.maxLength(USERNAME_MAX_LENGTH)]],
            lastName: ['', [Validators.required, Validators.maxLength(USERNAME_MAX_LENGTH)]],
            password: ['', [Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
            email: ['', [Validators.required, Validators.minLength(this.EMAIL_MIN_LENGTH), Validators.maxLength(this.EMAIL_MAX_LENGTH)]],
            visibleRegistrationNumber: ['', [Validators.maxLength(this.REGISTRATION_NUMBER_MAX_LENGTH)]],
            activated: [''],
            isTestUser: [''],
            langKey: [''],
            authorities: [''],
            internal: [{ disabled: true }], // initially disabled, will be enabled if user.id is undefined
        });
        // Conditionally enable or disable 'internal' input based on user.id
        if (this.user().id !== undefined) {
            this.editForm.get('internal')?.disable(); // Artemis does not support to edit the internal flag for existing users
        } else {
            this.editForm.get('internal')?.enable(); // New users can either be internal or external
        }
        this.editForm.patchValue(this.user());
    }

    /**
     * Handles successful save by resetting state and navigating to previous page.
     */
    private onSaveSuccess(): void {
        this.isSaving.set(false);
        this.previousState();
    }

    /**
     * Handles save error by resetting the saving state.
     */
    private onSaveError(): void {
        this.isSaving.set(false);
    }

    /**
     * Filter the groups based on the input value
     * @param value input value
     */
    private filter(value: string): string[] {
        const filterValue = value.toLowerCase();
        return this.allGroups.filter((group) => group != undefined && group.toLowerCase().includes(filterValue));
    }

    /**
     * Adds a group to the user if it is valid
     * @param user to add the group to
     * @param groupString group to add
     */
    private addGroup(user: User, groupString: string) {
        if (groupString && this.allGroups.includes(groupString) && !user.groups?.includes(groupString)) {
            // Replace the array (not push) so the shallow `commitUser` spread yields a NEW `groups` reference: the
            // one-way `[ngModel]` on the autocomplete only calls writeValue (→ renders the chip) when the reference
            // changes. An in-place push kept the same reference, so an Enter-added group never showed a chip.
            user.groups = [...(user.groups ?? []), groupString];
            this.commitUser(user);
        }
    }

    /**
     * Rebuild the user signal reference after an in-place mutation so the dependent template (chip list) re-renders under zoneless.
     * Only rebuilds when the mutated object is the currently held user to avoid clobbering unrelated state.
     */
    private commitUser(user: User) {
        this.user.update((currentUser) => (currentUser === user ? deepClone(currentUser) : currentUser));
    }

    /**
     * Get the translation key for an authority
     * @param authority the authority string (e.g., ROLE_ADMIN)
     */
    getAuthorityTranslationKey(authority: string): string {
        return this.authorityTranslationKeys[authority] ?? authority;
    }

    /**
     * Check if the user has a specific authority
     * @param authority the authority to check
     */
    hasAuthority(authority: string): boolean {
        const authorities = this.editForm.get('authorities')?.value;
        return Array.isArray(authorities) && authorities.includes(authority);
    }

    /**
     * Toggle an authority on or off for the user
     * @param authority the authority to toggle
     */
    toggleAuthority(authority: string): void {
        const authoritiesControl = this.editForm.get('authorities');
        const currentAuthorities: string[] = authoritiesControl?.value ?? [];

        if (currentAuthorities.includes(authority)) {
            authoritiesControl?.setValue(currentAuthorities.filter((a) => a !== authority));
        } else {
            authoritiesControl?.setValue([...currentAuthorities, authority]);
        }
    }
}
