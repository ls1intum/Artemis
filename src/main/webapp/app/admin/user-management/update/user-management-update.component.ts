import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/admin/organization-selector/organization-selector.component';
import { Organization } from 'app/admin/organization-management/organization.model';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCheckboxComponent,
    TumUiChipComponent,
    TumUiDialogComponent,
    TumUiFormFieldComponent,
    TumUiInputDirective,
    TumUiSelectComponent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, PROFILE_JENKINS, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FindLanguageFromKeyPipe } from 'app/foundation/language/find-language-from-key.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AccountService } from 'app/core/auth/account.service';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { Authority } from 'app/foundation/constants/authority.constants';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

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
        TumUiFormFieldComponent,
        TumUiInputDirective,
        TumUiCheckboxComponent,
        TumUiSelectComponent,
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
    private readonly route = inject(ActivatedRoute);
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);
    private readonly fb = inject(FormBuilder);
    private readonly accountService = inject(AccountService);
    private readonly credentialRevocationConfirmationService = inject(CredentialRevocationConfirmationService);
    private readonly destroyRef = inject(DestroyRef);

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

    /** Whether an administrator explicitly chose to revoke the existing user's other credentials with a password change. */
    readonly revokeCredentials = signal(false);

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
     * Initializes the component by loading user data, authorities and languages.
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
        this.isJenkins = this.profileService.isProfileActive(PROFILE_JENKINS);
        this.userService.authorities().subscribe((authorities) => {
            this.authorities.set(
                this.accountService.isSuperAdmin() ? authorities : authorities.filter((authority) => authority !== Authority.SUPER_ADMIN && authority !== Authority.ADMIN),
            );
        });
        this.languages.set(this.languageHelper.getAll());
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
    async save(): Promise<void> {
        // temporarily store the user organizations because they are not part of the edit form
        const userOrganizations = this.user().organizations;
        const updatedUser: User = this.editForm.getRawValue();
        updatedUser.organizations = userOrganizations;
        if (updatedUser.id) {
            updatedUser.revokeCredentials = !!updatedUser.password && this.revokeCredentials();
        }

        // Deactivating an active account also revokes every credential, in UserCreationService.updateUser and regardless
        // of the checkbox, so clearing "Activated" and saving deletes all passkeys, keys and tokens too. Confirming only
        // the checkbox left that path silent, which is the more surprising of the two: the administrator was not asked
        // about credentials at all.
        const deactivating = this.user().id !== undefined && this.user().activated && !updatedUser.activated;

        // Confirmed before saving, and before the spinner starts, because this deletes another person's authenticators
        // and keys irreversibly. An administrator has no way to notice a mistyped click here the way the owner would, so
        // this is the site that most needs the question asked. A save that revokes nothing is not interrupted.
        if (updatedUser.revokeCredentials || deactivating) {
            const confirmed = await this.credentialRevocationConfirmationService.confirm({ passkeys: true, sshKeys: true, vcsAccessTokens: true });
            if (!confirmed) {
                return;
            }
        }

        this.isSaving.set(true);
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
        // Clears the typed value as well, not just the model: save() submits editForm.getRawValue(), so a
        // password typed before toggling back would otherwise still be sent — silently changing the password
        // while revokeCredentials is reset to false, i.e. a real credential change that leaves the user's
        // other credentials intact.
        this.updatePasswordValidators(true);
        if (useRandomPassword) {
            this.revokeCredentials.set(false);
        }
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

    /**
     * Recomputes the password validators from the current state, and clears the value when a password no longer
     * applies.
     * <p>
     * Owned here rather than by a template `[required]` binding because the password input is rendered inside
     * both `@if (internal)` and `@if (!useRandomPassword())`. Whenever either turns off, the input and any
     * validator directive on it are destroyed while a rule left composed on the control keeps the form invalid —
     * with no field on screen for the administrator to fix, so Save stays disabled for good. Driving it from
     * state instead covers every route out of manual-password mode: toggling back to keeping the password, and
     * switching a new user to external.
     *
     * @param clearTypedValue whether to discard a password already typed, used when the mode itself changed
     */
    private updatePasswordValidators(clearTypedValue = false) {
        const passwordControl = this.editForm?.get('password');
        if (!passwordControl) {
            return;
        }
        const lengthRules = [Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)];
        const passwordApplies = !!this.editForm.get('internal')?.value && !this.useRandomPassword();
        passwordControl.setValidators(passwordApplies ? [Validators.required, ...lengthRules] : lengthRules);
        if (clearTypedValue || !passwordApplies) {
            // reset() re-runs the validators just set.
            passwordControl.reset('');
        } else {
            passwordControl.updateValueAndValidity();
        }
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
        // Recompute whenever `internal` changes: unchecking it hides the whole password section without going
        // through shouldRandomizePassword(), which would otherwise leave a required rule stranded on a hidden
        // control. initializeForm() returns early when the form already exists, so this subscribes once.
        this.editForm
            .get('internal')
            ?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.updatePasswordValidators());
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
