/**
 * Vitest tests for UserManagementUpdateComponent.
 * Tests the create/edit form for user management with authority selection,
 * organization management, and group assignment.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Observable, of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, RouterState } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import * as Sentry from '@sentry/angular';

import { UserManagementUpdateComponent } from 'app/admin/user-management/update/user-management-update.component';
import { User } from 'app/account/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { Authority } from 'app/foundation/constants/authority.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Organization } from 'app/admin/organization-management/organization.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { PROFILE_JENKINS } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { TumUiCheckboxComponent } from '@tumaet/ui-angular';

// Mock Sentry before tests run to prevent actual error reporting
vi.mock('@sentry/angular', async () => {
    const actual = await vi.importActual('@sentry/angular');
    return {
        ...actual,
        captureException: vi.fn(),
    };
});

const testBedProviders = [
    LocalStorageService,
    SessionStorageService,
    { provide: TranslateService, useClass: MockTranslateService },
    { provide: Router, useClass: MockRouter },
    { provide: ProfileService, useClass: MockProfileService },
    provideHttpClient(),
    provideHttpClientTesting(),
];

describe('UserManagementUpdateComponent', () => {
    let component: UserManagementUpdateComponent;
    let fixture: ComponentFixture<UserManagementUpdateComponent>;
    let adminUserService: AdminUserService;
    let titleService: Title;
    let translateService: TranslateService;
    let profileService: ProfileService;

    /** Test user data loaded from parent route */
    const testUser = new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.STUDENT]);

    /** Mock parent route containing user data from resolver */
    const parentRoute = {
        data: of({ user: testUser }),
    } as unknown as ActivatedRoute;
    const mockRoute = { parent: parentRoute } as unknown as ActivatedRoute;

    /** Mock router state for language helper tests */
    let mockRouterState: RouterState;

    beforeEach(async () => {
        parentRoute.data = of({ user: testUser });
        await TestBed.configureTestingModule({
            imports: [UserManagementUpdateComponent],
            providers: [
                { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
                { provide: ActivatedRoute, useValue: mockRoute },
                ...testBedProviders,
            ],
        })
            .overrideTemplate(UserManagementUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UserManagementUpdateComponent);
        component = fixture.componentInstance;
        adminUserService = TestBed.inject(AdminUserService);
        titleService = TestBed.inject(Title);
        translateService = TestBed.inject(TranslateService);
        profileService = TestBed.inject(ProfileService);

        mockRouterState = {
            snapshot: {
                root: { firstChild: {}, data: {} },
            },
        } as RouterState;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('ngOnInit', () => {
        it('should load authorities and languages on initialization', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);

            vi.spyOn(adminUserService, 'authorities').mockReturnValue(of(['USER']));
            const getAllSpy = vi.spyOn(languageHelper, 'getAll').mockReturnValue([]);
            const profileInfoSpy = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: ['jenkins'] } as ProfileInfo);

            component.ngOnInit();

            expect(adminUserService.authorities).toHaveBeenCalledOnce();
            expect(component.authorities()).toEqual(['USER']);
            expect(getAllSpy).toHaveBeenCalledOnce();
            expect(profileInfoSpy).toHaveBeenCalledOnce();
        });

        it('should load available languages on initialization', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);

            const getAllSpy = vi.spyOn(languageHelper, 'getAll');
            const profileInfoSpy = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: ['jenkins'] } as ProfileInfo);

            component.ngOnInit();

            expect(getAllSpy).toHaveBeenCalledOnce();
            expect(component.languages()).toEqual(LANGUAGES);
            expect(profileInfoSpy).toHaveBeenCalledOnce();
        });

        it('should return current language from translate service', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);
            const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as unknown as typeof MockRouter);
            routerMock.setRouterState(mockRouterState);

            translateService.use('en');

            languageHelper.language.subscribe((res) => expect(res).toEqual(translateService.getCurrentLang()));
        });

        it('should set page title based on router snapshot', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);
            const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as unknown as typeof MockRouter);
            mockRouterState.snapshot.root.data = { pageTitle: 'parent.page.test' };
            mockRouterState.snapshot.root.firstChild!.data = { pageTitle: 'child.page.test' };
            routerMock.setRouterState(mockRouterState);

            const updateTitleSpy = vi.spyOn(languageHelper, 'updateTitle');
            const getPageTitleSpy = vi.spyOn(languageHelper, 'getPageTitle');
            const setTitleSpy = vi.spyOn(titleService, 'setTitle');

            translateService.use('en');

            expect(updateTitleSpy).toHaveBeenCalledOnce();
            expect(getPageTitleSpy).toHaveBeenCalledTimes(2);
            expect(getPageTitleSpy).toHaveBeenNthCalledWith(1, mockRouterState.snapshot.root);
            expect(getPageTitleSpy).toHaveBeenNthCalledWith(2, mockRouterState.snapshot.root.firstChild);
            expect(getPageTitleSpy).toHaveLastReturnedWith('child.page.test');
            expect(setTitleSpy).toHaveBeenCalledOnce();
            expect(setTitleSpy).toHaveBeenCalledWith('child.page.test');
        });

        it('should set page title to default when no page title in route', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);
            const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as unknown as typeof MockRouter);
            routerMock.setRouterState(mockRouterState);

            const updateTitleSpy = vi.spyOn(languageHelper, 'updateTitle');
            const setTitleSpy = vi.spyOn(titleService, 'setTitle');

            translateService.use('en');

            expect(updateTitleSpy).toHaveBeenCalledOnce();
            expect(setTitleSpy).toHaveBeenCalledOnce();
            expect(setTitleSpy).toHaveBeenCalledWith('global.title');
        });

        it('should capture exception if title translation not found', () => {
            const languageHelper = TestBed.inject(JhiLanguageHelper);
            const routerMock: MockRouter = TestBed.inject<MockRouter>(Router as unknown as typeof MockRouter);
            routerMock.setRouterState(mockRouterState);

            const updateTitleSpy = vi.spyOn(languageHelper, 'updateTitle');
            const getTranslationSpy = vi.spyOn(translateService, 'get').mockReturnValue(of(undefined));
            const setTitleSpy = vi.spyOn(titleService, 'setTitle');
            const captureExceptionSpy = vi.spyOn(Sentry, 'captureException');

            translateService.use('en');

            expect(updateTitleSpy).toHaveBeenCalledOnce();
            expect(getTranslationSpy).toHaveBeenCalledOnce();
            expect(getTranslationSpy).toHaveBeenCalledWith('global.title');
            expect(captureExceptionSpy).toHaveBeenCalledOnce();
            expect(captureExceptionSpy).toHaveBeenCalledWith(new Error("Translation key 'global.title' for page title not found"));
            expect(setTitleSpy).not.toHaveBeenCalled();
        });

        it('should initialize edit form with correct controls', () => {
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: ['jenkins'] } as ProfileInfo);

            component.ngOnInit();

            expect(component.editForm.controls['id']).toBeDefined();
            expect(component.editForm.controls['isTestUser']).toBeDefined();
        });

        it('should include SUPER_ADMIN authority when current user is a super admin', () => {
            // GIVEN
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isSuperAdmin').mockReturnValue(true);
            vi.spyOn(adminUserService, 'authorities').mockReturnValue(of([Authority.STUDENT, Authority.ADMIN, Authority.SUPER_ADMIN]));
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: ['jenkins'] } as ProfileInfo);

            // WHEN
            component.ngOnInit();

            // THEN
            expect(adminUserService.authorities).toHaveBeenCalledOnce();
            expect(accountService.isSuperAdmin).toHaveBeenCalledOnce();
            expect(component.authorities()).toEqual([Authority.STUDENT, Authority.ADMIN, Authority.SUPER_ADMIN]);
        });

        it('should filter out SUPER_ADMIN and ADMIN authority when current user is not a super admin', () => {
            // GIVEN
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isSuperAdmin').mockReturnValue(false);
            vi.spyOn(adminUserService, 'authorities').mockReturnValue(of([Authority.STUDENT, Authority.ADMIN, Authority.SUPER_ADMIN, Authority.INSTRUCTOR]));
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: ['jenkins'] } as ProfileInfo);

            // WHEN
            component.ngOnInit();

            // THEN
            expect(adminUserService.authorities).toHaveBeenCalledOnce();
            expect(accountService.isSuperAdmin).toHaveBeenCalledOnce();
            expect(component.authorities()).toEqual([Authority.STUDENT, Authority.INSTRUCTOR]);
            expect(component.authorities()).not.toContain(Authority.SUPER_ADMIN);
            expect(component.authorities()).not.toContain(Authority.ADMIN);
        });
    });

    describe('save', () => {
        it('should call update service when saving existing user', async () => {
            const existingUser = new User(123);
            const createSpy = vi.spyOn(adminUserService, 'create');
            const updateSpy = vi.spyOn(adminUserService, 'update').mockReturnValue(
                of(
                    new HttpResponse({
                        body: existingUser,
                    }),
                ),
            );
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: 'new-Password-123' });

            await component.save();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ id: 123, login: 'test_user', password: 'new-Password-123', revokeCredentials: false }));
            expect(createSpy).not.toHaveBeenCalled();
            expect(component.user()).toBe(updateSpy.mock.calls[0][0]);
            expect(component.revokeCredentials()).toBe(false);
            expect(component.isSaving()).toBe(false);
        });

        it('should request credential revocation when explicitly selected for an existing user password change', async () => {
            const existingUser = new User(123);
            const createSpy = vi.spyOn(adminUserService, 'create');
            const updateSpy = vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: existingUser })));
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: 'new-Password-123' });
            component.revokeCredentials.set(true);

            await component.save();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ id: 123, login: 'test_user', password: 'new-Password-123', revokeCredentials: true }));
            expect(createSpy).not.toHaveBeenCalled();
            expect(component.user().revokeCredentials).toBe(true);
            expect(component.isSaving()).toBe(false);
        });

        it("should ask before revoking another user's credentials, and not save when dismissed", async () => {
            // The administrator is deleting someone else's authenticators and keys irreversibly, and unlike the owner they
            // have no way to notice a mistyped click afterwards. Dismissing must leave the account untouched entirely.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm').mockResolvedValue(false);
            const updateSpy = vi.spyOn(adminUserService, 'update');
            const existingUser = new User(123);
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: 'new-Password-123' });
            component.revokeCredentials.set(true);

            await component.save();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: true, sshKeys: true, vcsAccessTokens: true });
            expect(updateSpy).not.toHaveBeenCalled();
            // The spinner must not be left running by an aborted save.
            expect(component.isSaving()).toBe(false);
        });

        it('should ask before deactivating an active user, because that revokes every credential', async () => {
            // UserCreationService.updateUser revokes everything for a deactivation regardless of the checkbox, so a save
            // with Activated cleared deletes all passkeys, keys and tokens. Confirming only the checkbox left that silent.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm').mockResolvedValue(false);
            const updateSpy = vi.spyOn(adminUserService, 'update');
            const existingUser = new User(123);
            existingUser.activated = true;
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ activated: false });

            await component.save();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: true, sshKeys: true, vcsAccessTokens: true });
            expect(updateSpy).not.toHaveBeenCalled();
            expect(component.isSaving()).toBe(false);
        });

        it('should not ask when a save revokes nothing', async () => {
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm');
            const existingUser = new User(123);
            vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: existingUser })));
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: 'new-Password-123' });
            component.revokeCredentials.set(false);

            await component.save();

            expect(confirmSpy).not.toHaveBeenCalled();
        });

        it('should not submit a typed password after toggling back to keeping the existing one', async () => {
            // Regression test: shouldRandomizePassword() used to reset only user().password, while save()
            // submits editForm.getRawValue(). A password typed before toggling back was therefore still
            // sent, silently changing it while revokeCredentials was forced to false — a real credential
            // change that left the user's other credentials intact.
            const existingUser = new User(123);
            const updateSpy = vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: existingUser })));
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            // The admin opts to set a password, types one, then changes their mind and keeps the old one.
            component.shouldRandomizePassword(false);
            component.editForm.patchValue({ password: 'typed-Password-123' });
            component.revokeCredentials.set(true);
            component.shouldRandomizePassword(true);

            await component.save();

            expect(updateSpy).toHaveBeenCalledOnce();
            const submitted = updateSpy.mock.calls[0][0];
            expect(submitted.password).toBeFalsy();
            expect(submitted.revokeCredentials).toBe(false);
            expect(component.editForm.get('password')?.value).toBe('');
        });

        it('should keep the form saveable when a new user with a manual password is switched to external', () => {
            // Third route out of manual-password mode, and the one that does not go through
            // shouldRandomizePassword() at all: the template hides the entire password section behind
            // `@if (internal)`, so unchecking it destroys the input while a required rule left on the control
            // would keep the form invalid — with no password field on screen to satisfy it.
            // A new user (no id) is required here, since `internal` is disabled for existing users.
            const newUser = new User();
            component.user.set(newUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.get('internal')!.enable();
            component.editForm.patchValue({ internal: true });
            const passwordControl = component.editForm.get('password')!;

            component.shouldRandomizePassword(false);
            component.editForm.patchValue({ password: 'typed-Password-123' });
            expect(passwordControl.valid).toBe(true);

            // The administrator decides the account is externally managed after all.
            component.editForm.patchValue({ internal: false });

            expect(passwordControl.errors).toBeNull();
            expect(passwordControl.valid).toBe(true);
            expect(passwordControl.value).toBe('');
        });

        it('should keep the form saveable after toggling back to keeping the existing password', () => {
            // Second regression guard, found by exercising this in the browser. The password input lives inside
            // `@if (!useRandomPassword())`, so toggling back destroys the RequiredValidator directive while its
            // required rule stays composed on the control. Clearing the value then left the control invalid, the
            // whole form invalid, and the Save button permanently disabled — so the fix above would have traded a
            // silent password change for an admin who cannot save at all.
            const existingUser = new User(123);
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            // An internal account: the password only applies to those, so the required rule is keyed on it.
            component.editForm.patchValue({ internal: true });
            const passwordControl = component.editForm.get('password')!;

            component.shouldRandomizePassword(false);
            expect(passwordControl.errors).toEqual({ required: true });

            component.editForm.patchValue({ password: 'typed-Password-123' });
            expect(passwordControl.valid).toBe(true);

            component.shouldRandomizePassword(true);

            expect(passwordControl.value).toBe('');
            expect(passwordControl.errors).toBeNull();
            expect(passwordControl.valid).toBe(true);
        });

        it('should never request credential revocation without a replacement password', async () => {
            const existingUser = new User(123);
            const updateSpy = vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: existingUser })));
            component.user.set(existingUser);
            component.user().login = 'test_user';
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: undefined });
            component.revokeCredentials.set(true);

            await component.save();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ id: 123, login: 'test_user', password: undefined, revokeCredentials: false }));
            expect(component.user().revokeCredentials).toBe(false);
            expect(component.isSaving()).toBe(false);
        });

        it('should call create service when saving new user', async () => {
            const newUser = new User();
            const createSpy = vi.spyOn(adminUserService, 'create').mockReturnValue(of(new HttpResponse({ body: newUser })));
            const updateSpy = vi.spyOn(adminUserService, 'update');
            component.user.set(newUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.editForm.patchValue({ password: 'new-Password-123' });
            component.revokeCredentials.set(true);

            await component.save();

            expect(createSpy).toHaveBeenCalledOnce();
            expect(updateSpy).not.toHaveBeenCalled();
            expect(createSpy.mock.calls[0][0]).not.toHaveProperty('revokeCredentials');
            expect(createSpy.mock.calls[0][0].id).toBeFalsy();
            expect(createSpy.mock.calls[0][0].password).toBe('new-Password-123');
            expect(component.isSaving()).toBe(false);
        });
    });

    it('should set isSaving to false on save error', () => {
        // @ts-ignore - accessing private method for testing
        component.onSaveError();
        expect(component.isSaving()).toBe(false);
    });

    it('should set password to undefined when using random password', () => {
        component.user.set({ password: 'abc' } as User);
        component.revokeCredentials.set(true);
        component.shouldRandomizePassword(true);
        expect(component.useRandomPassword()).toBe(true);
        expect(component.user().password).toBeUndefined();
        expect(component.revokeCredentials()).toBe(false);

        component.shouldRandomizePassword(false);
        expect(component.useRandomPassword()).toBe(false);
        expect(component.user().password).toBe('');
        expect(component.revokeCredentials()).toBe(false);
    });

    it('should open organizations modal and add selected organization', () => {
        const existingOrganization = {} as Organization;
        component.user.set({ organizations: [existingOrganization] } as User);

        component.openOrganizationsModal();

        expect(component.orgSelectorVisible()).toBe(true);

        // Simulate selecting a new organization via the declarative selector dialog's output
        const newOrganization = {} as Organization;
        component.onOrgSelected(newOrganization);
        // Check component.user().organizations directly since immutable operations create a new array
        expect(component.user().organizations).toContain(existingOrganization);
        expect(component.user().organizations).toContain(newOrganization);
        expect(component.user().organizations).toHaveLength(2);

        // Test when user has no organizations yet
        component.user().organizations = undefined;
        component.onOrgSelected(newOrganization);
        expect(component.user().organizations).toEqual([newOrganization]);
    });

    it('should remove organization from user', () => {
        const organization1 = { id: 1 };
        const organization2 = { id: 2 };
        component.user.set({ organizations: [organization1, organization2] } as User);

        component.removeOrganizationFromUser(organization2);

        expect(component.user().organizations).toEqual([organization1]);
    });

    describe('previousState', () => {
        it('should navigate to user detail page when editing existing user', () => {
            const routerMock = TestBed.inject(Router) as unknown as MockRouter;
            component.user.set({ id: 123, login: 'testuser' } as User);

            component.previousState();

            expect(routerMock.navigate).toHaveBeenCalled();
        });

        it('should navigate to user management overview when creating new user', () => {
            const routerMock = TestBed.inject(Router) as unknown as MockRouter;
            component.user.set({ id: undefined } as unknown as User);

            component.previousState();

            expect(routerMock.navigate).toHaveBeenCalled();
        });
    });

    it('should not modify organizations when the selector is cancelled', () => {
        component.user.set({ organizations: [{ id: 1 }] as Organization[] } as User);

        component.openOrganizationsModal();
        expect(component.orgSelectorVisible()).toBe(true);

        // Cancelling closes the dialog (orgSelectorVisible -> false) without adding an organization
        component.orgSelectorVisible.set(false);

        expect(component.user().organizations).toHaveLength(1);
    });

    describe('ngOnInit - additional coverage', () => {
        it('should fetch organizations for existing user', () => {
            const organizationService = TestBed.inject(OrganizationManagementService);
            const mockOrganizations = [{ id: 1, name: 'Org1' }] as Organization[];
            vi.spyOn(organizationService, 'getOrganizationsByUser').mockReturnValue(of(mockOrganizations));

            component.ngOnInit();

            expect(organizationService.getOrganizationsByUser).toHaveBeenCalledWith(testUser.id);
            expect(component.user().organizations).toEqual(mockOrganizations);
        });
    });

    describe('save - additional coverage', () => {
        it('should show Jenkins warning when login changes and no password set', async () => {
            const alertService = TestBed.inject(AlertService);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');

            // Mock isProfileActive before ngOnInit to set isJenkins = true
            vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_JENKINS);

            // Initialize component through ngOnInit to set isJenkins flag
            component.ngOnInit();

            // Setup existing user with different login
            component.user.set(new User(123));
            component.user().login = 'new_login';
            component.user().password = undefined;
            // @ts-ignore - accessing private property for testing
            component.oldLogin = 'old_login';

            // Reset editForm to use new values
            // @ts-ignore - accessing private property for testing
            component.editForm = undefined;
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: component.user() })));

            await component.save();

            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.WARNING,
                message: 'artemisApp.userManagement.jenkinsChange',
                timeout: 0,
                translationParams: { oldLogin: 'old_login', newLogin: 'new_login' },
            });
        });

        it('should not show Jenkins warning when login stays the same', async () => {
            const alertService = TestBed.inject(AlertService);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_JENKINS);

            component.ngOnInit();

            component.user.set(new User(123));
            component.user().login = 'same_login';
            // @ts-ignore - accessing private property for testing
            component.oldLogin = 'same_login';

            // Reset editForm to use new values
            // @ts-ignore - accessing private property for testing
            component.editForm = undefined;
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: component.user() })));

            await component.save();

            expect(addAlertSpy).not.toHaveBeenCalled();
        });

        it('should handle update error correctly', async () => {
            const existingUser = new User(123);
            existingUser.login = 'test_user';
            vi.spyOn(adminUserService, 'update').mockReturnValue(throwError(() => new Error('Update failed')));
            component.user.set(existingUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.isSaving.set(true);

            await component.save();

            expect(component.isSaving()).toBe(false);
        });

        it('should handle create error correctly', async () => {
            const newUser = new User();
            vi.spyOn(adminUserService, 'create').mockReturnValue(throwError(() => new Error('Create failed')));
            component.user.set(newUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
            component.isSaving.set(true);

            await component.save();

            expect(component.isSaving()).toBe(false);
        });

        it('should preserve organizations when saving', async () => {
            const existingUser = new User(123);
            existingUser.login = 'test_user';
            existingUser.organizations = [{ id: 1 }] as Organization[];
            vi.spyOn(adminUserService, 'update').mockReturnValue(of(new HttpResponse({ body: existingUser })));
            component.user.set(existingUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            await component.save();

            expect(component.user().organizations).toEqual([{ id: 1 }]);
        });
    });

    describe('initializeForm', () => {
        it('should return early if editForm already exists', () => {
            // Initialize user first to avoid undefined error
            component.user.set(new User(123));

            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            const firstForm = component.editForm;

            // Call again - should return early without recreating
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            expect(component.editForm).toBe(firstForm);
        });

        it('should enable internal field for new users', () => {
            component.user.set(new User()); // No id = new user
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            expect(component.editForm.get('internal')?.enabled).toBe(true);
        });

        it('should disable internal field for existing users', () => {
            component.user.set(new User(123)); // Has id = existing user
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            expect(component.editForm.get('internal')?.disabled).toBe(true);
        });

        it('should patch the isTestUser flag so the checkbox reflects the persisted value', () => {
            const testUser = new User(123);
            testUser.isTestUser = true;
            component.user.set(testUser);
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            expect(component.editForm.get('isTestUser')?.value).toBe(true);
        });

        it('should leave the isTestUser checkbox unchecked for a user that is not flagged', () => {
            component.user.set(new User(123));
            // @ts-ignore - accessing private method for testing
            component.initializeForm();

            expect(component.editForm.get('isTestUser')?.value).toBeFalsy();
        });
    });

    describe('authority management', () => {
        beforeEach(() => {
            component.user.set(new User(123));
            // @ts-ignore - accessing private method for testing
            component.initializeForm();
        });

        it('should return translation key for known authority', () => {
            expect(component.getAuthorityTranslationKey('ROLE_ADMIN')).toBe('artemisApp.userManagement.roles.admin');
            expect(component.getAuthorityTranslationKey('ROLE_INSTRUCTOR')).toBe('artemisApp.userManagement.roles.instructor');
            expect(component.getAuthorityTranslationKey('ROLE_EDITOR')).toBe('artemisApp.userManagement.roles.editor');
            expect(component.getAuthorityTranslationKey('ROLE_TA')).toBe('artemisApp.userManagement.roles.tutor');
            expect(component.getAuthorityTranslationKey('ROLE_USER')).toBe('artemisApp.userManagement.roles.user');
        });

        it('should return authority itself for unknown authority', () => {
            expect(component.getAuthorityTranslationKey('ROLE_UNKNOWN')).toBe('ROLE_UNKNOWN');
        });

        it('should check if user has authority', () => {
            component.editForm.get('authorities')?.setValue(['ROLE_ADMIN', 'ROLE_USER']);

            expect(component.hasAuthority('ROLE_ADMIN')).toBe(true);
            expect(component.hasAuthority('ROLE_USER')).toBe(true);
            expect(component.hasAuthority('ROLE_INSTRUCTOR')).toBe(false);
        });

        it('should return false for hasAuthority when authorities is not an array', () => {
            component.editForm.get('authorities')?.setValue(null);
            expect(component.hasAuthority('ROLE_ADMIN')).toBe(false);
        });

        it('should toggle authority on when not present', () => {
            component.editForm.get('authorities')?.setValue(['ROLE_USER']);

            component.toggleAuthority('ROLE_ADMIN');

            expect(component.editForm.get('authorities')?.value).toEqual(['ROLE_USER', 'ROLE_ADMIN']);
        });

        it('should toggle authority off when present', () => {
            component.editForm.get('authorities')?.setValue(['ROLE_ADMIN', 'ROLE_USER']);

            component.toggleAuthority('ROLE_ADMIN');

            expect(component.editForm.get('authorities')?.value).toEqual(['ROLE_USER']);
        });

        it('should handle toggle when authorities is null', () => {
            component.editForm.get('authorities')?.setValue(null);

            component.toggleAuthority('ROLE_ADMIN');

            expect(component.editForm.get('authorities')?.value).toEqual(['ROLE_ADMIN']);
        });

        it('should sort authorities by role hierarchy', () => {
            // Set authorities in random order
            component.authorities.set(['ROLE_USER', 'ROLE_EDITOR', 'ROLE_SUPER_ADMIN', 'ROLE_TA', 'ROLE_ADMIN', 'ROLE_INSTRUCTOR']);

            // Get sorted authorities
            const sorted = component.sortedAuthorities();

            // Verify correct order: super admin > admin > instructor > editor > tutor > user
            expect(sorted).toEqual(['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_EDITOR', 'ROLE_TA', 'ROLE_USER']);
        });

        it('should reactively update sortedAuthorities when authorities signal changes', () => {
            // Initially set some authorities
            component.authorities.set(['ROLE_USER', 'ROLE_ADMIN']);
            expect(component.sortedAuthorities()).toEqual(['ROLE_ADMIN', 'ROLE_USER']);

            // Update authorities signal
            component.authorities.set(['ROLE_INSTRUCTOR', 'ROLE_TA', 'ROLE_SUPER_ADMIN']);

            // Verify sortedAuthorities updated automatically
            expect(component.sortedAuthorities()).toEqual(['ROLE_SUPER_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']);
        });

        it('should handle unknown authorities in sorting', () => {
            // Set authorities including unknown ones
            component.authorities.set(['ROLE_UNKNOWN', 'ROLE_ADMIN', 'ROLE_CUSTOM']);

            const sorted = component.sortedAuthorities();

            // Known roles should come first, unknown roles last (sorted by their fallback value of 999)
            expect(sorted[0]).toBe('ROLE_ADMIN');
            expect(sorted).toContain('ROLE_UNKNOWN');
            expect(sorted).toContain('ROLE_CUSTOM');
        });
    });
});

describe('UserManagementUpdateComponent credential revocation controls', () => {
    let component: UserManagementUpdateComponent;
    let fixture: ComponentFixture<UserManagementUpdateComponent>;
    let parentRoute: { data: Observable<{ user: User | undefined }> };

    beforeEach(async () => {
        parentRoute = { data: of({ user: undefined }) };
        const route = { parent: parentRoute } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [UserManagementUpdateComponent],
            providers: [
                { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
                { provide: ActivatedRoute, useValue: route },
                ...testBedProviders,
            ],
        }).compileComponents();

        vi.spyOn(TestBed.inject(AdminUserService), 'authorities').mockReturnValue(of([]));
        vi.spyOn(TestBed.inject(OrganizationManagementService), 'getOrganizationsByUser').mockReturnValue(of([]));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    async function render(user: User): Promise<void> {
        parentRoute.data = of({ user });
        fixture = TestBed.createComponent(UserManagementUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function checkboxInput(id: string): HTMLInputElement | null {
        return fixture.nativeElement.querySelector(`input#${id}`);
    }

    it.each(['login', 'firstName', 'lastName', 'email', 'visibleRegistrationNumber'])('associates a visible label with the %s control', async (controlId) => {
        await render(new User(123, 'test_user', 'Test', 'User', 'test@example.com', true, 'en', [Authority.STUDENT]));

        const label = fixture.nativeElement.querySelector(`label[for="${controlId}"]`);
        expect(label).not.toBeNull();
        expect(fixture.nativeElement.querySelector(`#${controlId}`)).not.toBeNull();
    });

    it('labels the password field, which previously only carried a placeholder', async () => {
        const existingUser = new User(123, 'test_user', 'Test', 'User', 'test@example.com', true, 'en', [Authority.STUDENT]);
        existingUser.internal = true;
        await render(existingUser);
        component.shouldRandomizePassword(false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('label[for="password"]')).not.toBeNull();
    });

    it('marks every required field with a marker hidden from assistive technology', async () => {
        await render(new User(123, 'test_user', 'Test', 'User', 'test@example.com', true, 'en', [Authority.STUDENT]));

        const markers = Array.from(fixture.nativeElement.querySelectorAll('.tum-ui-form-field-required')) as HTMLElement[];

        expect(markers.length).toBe(4);
        markers.forEach((marker) => expect(marker.getAttribute('aria-hidden')).toBe('true'));
    });

    it.each(['login', 'firstName', 'lastName', 'email'])('tells the user why %s is invalid once it is cleared', async (controlId) => {
        await render(new User(123, 'test_user', 'Test', 'User', 'test@example.com', true, 'en', [Authority.STUDENT]));

        const control = component.editForm.get(controlId)!;
        control.setValue('');
        control.markAsDirty();
        fixture.detectChanges();

        // The field carries a required marker and points aria-describedby at this region, so leaving it empty
        // would tell a screen reader that something is wrong without ever saying what.
        const error = fixture.nativeElement.querySelector(`#${controlId}`).closest('tum-ui-form-field').querySelector('.tum-ui-form-field-error');
        expect(error.hasAttribute('hidden')).toBe(false);
        expect(error.textContent.trim()).not.toBe('');
    });

    it('should show an unchecked opt-in only while replacing an existing internal user password', async () => {
        const existingUser = new User(123, 'test_user', 'Test', 'User', 'test@example.com', true, 'en', [Authority.STUDENT]);
        existingUser.internal = true;
        await render(existingUser);

        const keepPasswordCheckbox = checkboxInput('randomPassword')!;
        expect(keepPasswordCheckbox).not.toBeNull();
        expect(keepPasswordCheckbox.checked).toBe(true);
        expect(component.useRandomPassword()).toBe(true);
        expect(fixture.nativeElement.querySelector('input#password')).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'))).toBeNull();

        keepPasswordCheckbox.click();
        fixture.detectChanges();

        const revokeHost = fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'));
        const revokeCheckbox = revokeHost.componentInstance as TumUiCheckboxComponent;
        const revokeInput = checkboxInput('revokeCredentials')!;
        expect(component.useRandomPassword()).toBe(false);
        expect(keepPasswordCheckbox.checked).toBe(false);
        expect(fixture.nativeElement.querySelector('input#password')).not.toBeNull();
        expect(revokeHost).not.toBeNull();
        expect(revokeInput.checked).toBe(false);
        expect(revokeCheckbox.checked()).toBe(false);
        expect(component.revokeCredentials()).toBe(false);
        expect(fixture.nativeElement.querySelector('label[for="revokeCredentials"]')).not.toBeNull();

        revokeInput.click();
        fixture.detectChanges();

        expect(revokeInput.checked).toBe(true);
        expect(revokeCheckbox.checked()).toBe(true);
        expect(component.revokeCredentials()).toBe(true);

        keepPasswordCheckbox.click();
        fixture.detectChanges();

        expect(component.useRandomPassword()).toBe(true);
        expect(component.revokeCredentials()).toBe(false);
        expect(fixture.nativeElement.querySelector('input#password')).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'))).toBeNull();
    });

    it('should not offer credential revocation while creating an internal user', async () => {
        const newUser = new User(undefined, 'new_user', 'New', 'User', 'new@example.com', true, 'en', [Authority.STUDENT]);
        newUser.internal = true;
        await render(newUser);

        const randomPasswordCheckbox = checkboxInput('randomPassword')!;
        expect(randomPasswordCheckbox).not.toBeNull();
        expect(randomPasswordCheckbox.checked).toBe(true);
        expect(fixture.nativeElement.querySelector('input#password')).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'))).toBeNull();

        randomPasswordCheckbox.click();
        fixture.detectChanges();

        expect(component.useRandomPassword()).toBe(false);
        expect(fixture.nativeElement.querySelector('input#password')).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'))).toBeNull();
    });

    it('should not show password or credential controls for an existing external user', async () => {
        const externalUser = new User(123, 'external_user', 'External', 'User', 'external@example.com', true, 'en', [Authority.STUDENT]);
        externalUser.internal = false;
        await render(externalUser);

        expect(component.editForm.get('internal')?.value).toBe(false);
        expect(checkboxInput('randomPassword')).toBeNull();
        expect(fixture.nativeElement.querySelector('input#password')).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="revoke-credentials"]'))).toBeNull();
        expect(component.revokeCredentials()).toBe(false);
    });
});
