import { ComponentFixture, TestBed } from '@angular/core/testing';

vi.mock('@sentry/angular', async (importOriginal) => {
    const originalModule = await importOriginal<typeof import('@sentry/angular')>();
    return {
        ...originalModule,
        captureException: vi.fn(),
    };
});

import * as Sentry from '@sentry/angular';
import { By } from '@angular/platform-browser';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { User } from 'app/account/user/user.model';
import { CodeButtonComponent, RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { ProgrammingExerciseTheiaConfig } from 'app/programming/shared/entities/programming-exercise-theia.config';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { expectedProfileInfo } from 'test/helpers/sample/profile-info-sample-data';

describe('CodeButtonComponent', () => {
    let component: CodeButtonComponent;
    let fixture: ComponentFixture<CodeButtonComponent>;
    let profileService: ProfileService;
    let accountService: AccountService;
    let sshUserSettingsService: SshUserSettingsService;
    let localStorageMock: LocalStorageService;
    let programmingExerciseService: ProgrammingExerciseService;

    let getVcsAccessTokenSpy: MockInstance;
    let createVcsAccessTokenSpy: MockInstance;
    let getCachedSshKeysSpy: MockInstance;
    const vcsToken: string = 'vcpat-xlhBs26D4F2CGlkCM59KVU8aaV9bYdX5Mg4IK6T8W3aT';

    const route = { snapshot: { url: of('courses') } } as any as ActivatedRoute;

    let localStorageState: RepositoryAuthenticationMethod = RepositoryAuthenticationMethod.SSH;
    let router: MockRouter;
    const info = expectedProfileInfo;

    let participation: ProgrammingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('a');

        await TestBed.configureTestingModule({
            imports: [ExerciseActionButtonComponent],
            providers: [
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                MockProvider(LocalStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeButtonComponent);
                component = fixture.componentInstance;
                profileService = TestBed.inject(ProfileService);
                accountService = TestBed.inject(AccountService);
                sshUserSettingsService = TestBed.inject(SshUserSettingsService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

                localStorageMock = fixture.debugElement.injector.get(LocalStorageService);

                getVcsAccessTokenSpy = vi.spyOn(accountService, 'getVcsAccessToken');
                getCachedSshKeysSpy = vi.spyOn(sshUserSettingsService, 'getCachedSshKeys');
                createVcsAccessTokenSpy = vi.spyOn(accountService, 'createVcsAccessToken');

                participation = { id: 5 };

                getVcsAccessTokenSpy = vi.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
                getCachedSshKeysSpy = vi
                    .spyOn(sshUserSettingsService, 'getCachedSshKeys')
                    .mockImplementation(() => Promise.resolve([{ id: 99, publicKey: 'key' } as UserSshPublicKey]));
                fixture.componentRef.setInput('repositoryUri', '');
                vi.spyOn(localStorageMock, 'retrieve').mockImplementation(() => {
                    return localStorageState;
                });
                vi.spyOn(localStorageMock, 'store').mockImplementation(() => {});
                createVcsAccessTokenSpy = vi
                    .spyOn(accountService, 'createVcsAccessToken')
                    .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'Bad Request' })));

                // Reset input properties
                fixture.componentRef.setInput('repositoryUri', '');
                fixture.componentRef.setInput('participations', []);
                fixture.componentRef.setInput('smallButtons', true);
                fixture.componentRef.setInput('routerLinkForRepositoryView', []);
                stubServices();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        localStorageState = RepositoryAuthenticationMethod.SSH;
    });

    it('should initialize', async () => {
        fixture.componentRef.setInput('participations', [participation]);

        await component.ngOnInit();
        component.onClick();
        fixture.detectChanges();
        expect(component.sshSettingsUrl).toBe(`${window.location.origin}/user-settings/ssh`);
        expect(component.sshTemplateUrl).toBe(info.sshCloneURLTemplate);
        expect(component.versionControlUrl).toBe(info.versionControlUrl);
        expect(getCachedSshKeysSpy).toHaveBeenCalled();
    });

    it('should default to token authentication when password was stored in local storage', async () => {
        localStorageState = RepositoryAuthenticationMethod.Password;
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        fixture.detectChanges();

        component.onClick();
        fixture.detectChanges();

        expect(component.selectedAuthenticationMechanism()).toBe(RepositoryAuthenticationMethod.Token);
        expect(component.useToken()).toBe(true);
        expect(localStorageMock.store).toHaveBeenCalledWith('code-button-state', RepositoryAuthenticationMethod.Token);
    });

    it('should preserve SSH preference from local storage', async () => {
        localStorageState = RepositoryAuthenticationMethod.SSH;
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        fixture.detectChanges();

        component.onClick();
        fixture.detectChanges();

        expect(component.selectedAuthenticationMechanism()).toBe(RepositoryAuthenticationMethod.SSH);
        expect(component.useSsh()).toBe(true);
    });

    it('should not load participation vcsAccessToken when it already exists in participation', async () => {
        participation.vcsAccessToken = 'vcpat-1234';
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        component.onClick();
        fixture.detectChanges();

        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).not.toHaveBeenCalled();
    });

    it('should load participation vcsAccessToken if it exists on the server', async () => {
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();
        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).not.toHaveBeenCalled();
    });

    it('should only display available authentication mechanisms', async () => {
        fixture.componentRef.setInput('participations', [participation]);
        localStorageState = RepositoryAuthenticationMethod.Password;
        await component.ngOnInit();

        component.authenticationMechanisms.set([RepositoryAuthenticationMethod.Token, RepositoryAuthenticationMethod.SSH]);
        component.onClick();
        fixture.detectChanges();
        expect(component.selectedAuthenticationMechanism()).toEqual(RepositoryAuthenticationMethod.Token);
    });

    describe('repository-scoped staff token', () => {
        const repoToken = 'vcpat-StaffStaffStaffStaffStaffStaffStaffStaff123';
        let getRepoTokenSpy: MockInstance;
        let createRepoTokenSpy: MockInstance;

        beforeEach(() => {
            getRepoTokenSpy = vi.spyOn(programmingExerciseService, 'getRepositoryVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: repoToken })));
            createRepoTokenSpy = vi.spyOn(programmingExerciseService, 'createRepositoryVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: repoToken })));
        });

        it('should load the repository-scoped token when opening the dialog for a base repository', async () => {
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exercise', { id: 42 } as ProgrammingExercise);
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-exercise.git');
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(component.isBaseRepository()).toBe(true);
            expect(getRepoTokenSpy).toHaveBeenCalledWith(42, 'TEMPLATE', undefined, undefined);
            expect(component.repositoryAccessToken()).toEqual(repoToken);
            expect(createRepoTokenSpy).not.toHaveBeenCalled();
        });

        it('should create the repository-scoped token on demand when none exists yet (404)', async () => {
            getRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            fixture.componentRef.setInput('repositoryType', 'SOLUTION');
            fixture.componentRef.setInput('exercise', { id: 42 } as ProgrammingExercise);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(createRepoTokenSpy).toHaveBeenCalledWith(42, 'SOLUTION', undefined, undefined);
            expect(component.repositoryAccessToken()).toEqual(repoToken);
        });

        it('should not load participation tokens while in base repository mode', async () => {
            fixture.componentRef.setInput('repositoryType', 'TESTS');
            fixture.componentRef.setInput('exercise', { id: 42 } as ProgrammingExercise);
            fixture.componentRef.setInput('participations', [participation]);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();
        });

        it('should load the repository-scoped token via the exerciseId input when no exercise object is provided', async () => {
            // The exercise detail view only knows the exercise id, not the full exercise object.
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'TEMPLATE', undefined, undefined);
            expect(component.repositoryAccessToken()).toEqual(repoToken);
        });

        it('should pass the auxiliaryRepositoryId when loading the token for an auxiliary repository', async () => {
            fixture.componentRef.setInput('repositoryType', 'AUXILIARY');
            fixture.componentRef.setInput('exerciseId', 7);
            fixture.componentRef.setInput('auxiliaryRepositoryId', 3);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'AUXILIARY', 3, undefined);
        });

        it('should reload a fresh token when the component is reused for a different base repository', async () => {
            // The repository view reuses one code-button instance across base repositories (only the route params change). A token minted for the previous repository must not be
            // reused for the next one, because repository tokens are scoped to one exact repository URI.
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-exercise.git');
            await component.ngOnInit();

            component.onClick();
            fixture.detectChanges();
            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'TEMPLATE', undefined, undefined);
            expect(component.repositoryAccessToken()).toEqual(repoToken);

            // Navigate to the solution repository of the same exercise: same component instance, only the repository type and URI change.
            const solutionToken = 'vcpat-SolutionSolutionSolutionSolutionSolutio123';
            getRepoTokenSpy.mockReturnValue(of(new HttpResponse({ body: solutionToken })));
            fixture.componentRef.setInput('repositoryType', 'SOLUTION');
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-solution.git');
            fixture.detectChanges();

            component.onClick();
            fixture.detectChanges();

            // A fresh token request is made for the new repository and the stale template token is replaced.
            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'SOLUTION', undefined, undefined);
            expect(component.repositoryAccessToken()).toEqual(solutionToken);
            // The embedded clone URL must use the new repository's token, never the stale template one.
            const cloneUrl = component.getHttpOrSshRepositoryUri(false, true, true);
            expect(cloneUrl).toContain(`:${solutionToken}@`);
            expect(cloneUrl).not.toContain(repoToken);
        });

        it('should ignore late repository token responses for a previously viewed base repository', async () => {
            const templateTokenSubject = new Subject<HttpResponse<string>>();
            const solutionTokenSubject = new Subject<HttpResponse<string>>();
            const solutionToken = 'vcpat-SolutionSolutionSolutionSolutionSolutio123';
            getRepoTokenSpy.mockReturnValueOnce(templateTokenSubject.asObservable()).mockReturnValueOnce(solutionTokenSubject.asObservable());
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-exercise.git');
            await component.ngOnInit();

            component.onClick();
            fixture.detectChanges();
            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'TEMPLATE', undefined, undefined);

            fixture.componentRef.setInput('repositoryType', 'SOLUTION');
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-solution.git');
            fixture.detectChanges();

            component.onClick();
            fixture.detectChanges();
            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'SOLUTION', undefined, undefined);

            templateTokenSubject.next(new HttpResponse({ body: repoToken }));
            templateTokenSubject.complete();
            expect(component.repositoryAccessToken()).toBeUndefined();
            expect(component.copyEnabled()).toBe(false);

            solutionTokenSubject.next(new HttpResponse({ body: solutionToken }));
            solutionTokenSubject.complete();

            expect(component.repositoryAccessToken()).toEqual(solutionToken);
            expect(component.copyEnabled()).toBe(true);
            const cloneUrl = component.getHttpOrSshRepositoryUri(false, true, true);
            expect(cloneUrl).toContain(`:${solutionToken}@`);
            expect(cloneUrl).not.toContain(repoToken);
        });

        it('should keep the copy button enabled for SSH when the repository token resolves after the dialog opened', async () => {
            // Reproduces the production ordering that synchronous of() mocks hide: the token HTTP response arrives only
            // after onClick already set the SSH copy state. The async response must not clobber it back to disabled.
            const tokenSubject = new Subject<HttpResponse<string>>();
            getRepoTokenSpy.mockReturnValue(tokenSubject.asObservable());
            localStorageState = RepositoryAuthenticationMethod.SSH;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();

            component.onClick();
            expect(component.useSsh()).toBe(true);
            // The user has SSH keys, so copy is enabled while the token request is still pending.
            expect(component.copyEnabled()).toBe(true);

            // The repository token resolves now, after the dialog already opened.
            tokenSubject.next(new HttpResponse({ body: repoToken }));
            tokenSubject.complete();

            expect(component.repositoryAccessToken()).toEqual(repoToken);
            expect(component.copyEnabled()).toBe(true);
        });

        it('should not show the manual VCS token warning for a base repository in course management', async () => {
            // In course management (e.g. the exercise detail page) the personal-token warning must never appear for base
            // repositories, because a repository-scoped staff token is provisioned automatically instead.
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();
            component.isInCourseManagement.set(true);
            fixture.detectChanges();

            fixture.debugElement.query(By.css('.code-button')).nativeElement.click();
            fixture.detectChanges();

            expect(component.isBaseRepository()).toBe(true);
            expect(getRepoTokenSpy).toHaveBeenCalledWith(7, 'TEMPLATE', undefined, undefined);
            expect(fixture.debugElement.query(By.css('.alert-warning'))).toBeNull();
        });

        it('should embed the repository-scoped token as the password in the clone URL', async () => {
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-exercise.git');
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            // The non-masked clone URL must embed the repository-scoped staff token as the password (not a participation or user token).
            const cloneUrl = component.getHttpOrSshRepositoryUri(false);
            expect(cloneUrl).toContain(`:${repoToken}@`);
            expect(component.copyEnabled()).toBe(true);
        });

        it('should warn when fetching the repository token is forbidden (403) and not attempt to create one', async () => {
            const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');
            getRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            // Select the token method so the copy button state reflects the (failed) token, not SSH.
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(warningSpy).toHaveBeenCalledWith('artemisApp.exerciseActions.repositoryAccessTokenForbidden');
            expect(createRepoTokenSpy).not.toHaveBeenCalled();
            expect(component.repositoryAccessToken()).toBeUndefined();
            expect(component.copyEnabled()).toBe(false);
        });

        it('should show an error when fetching the repository token fails unexpectedly', async () => {
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            getRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            fixture.componentRef.setInput('repositoryType', 'TEMPLATE');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.exerciseActions.repositoryAccessTokenError');
            expect(createRepoTokenSpy).not.toHaveBeenCalled();
        });

        it('should warn when creating the repository token (after a 404) is forbidden (403)', async () => {
            const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');
            getRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            createRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            fixture.componentRef.setInput('repositoryType', 'SOLUTION');
            fixture.componentRef.setInput('exerciseId', 7);
            await component.ngOnInit();
            component.onClick();
            fixture.detectChanges();

            expect(createRepoTokenSpy).toHaveBeenCalledWith(7, 'SOLUTION', undefined, undefined);
            expect(warningSpy).toHaveBeenCalledWith('artemisApp.exerciseActions.repositoryAccessTokenForbidden');
            expect(component.repositoryAccessToken()).toBeUndefined();
        });

        it('should not crash for a base repository whose view passes a participation-less array (tests/auxiliary repo)', async () => {
            // The staff tests/auxiliary repository view has no student participation and renders the code button with a
            // participation-less array. activeParticipation must tolerate that instead of dereferencing undefined (regression).
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('repositoryType', 'TESTS');
            fixture.componentRef.setInput('exerciseId', 7);
            fixture.componentRef.setInput('repositoryUri', 'http://localhost/git/TEST/test-tests.git');
            fixture.componentRef.setInput('participations', [undefined as unknown as ProgrammingExerciseStudentParticipation]);
            await component.ngOnInit();

            expect(() => component.activeParticipation()).not.toThrow();
            expect(component.activeParticipation()).toBeUndefined();

            component.onClick();
            fixture.detectChanges();

            // The clone URL is built from the base repository URI plus the repository-scoped token, with no participation involved.
            const cloneUrl = component.getHttpOrSshRepositoryUri(false);
            expect(cloneUrl).toContain('test-tests.git');
            expect(cloneUrl).toContain(`:${repoToken}@`);
        });

        it('should mint a repository-scoped token for a student repository browsed by staff and never leave the clone button disabled', async () => {
            // Course staff opening another student's assignment repository in course management. Even without a personal VCS
            // token, a repository-scoped USER token is provisioned on demand (keyed by the participation), so the copy button
            // becomes enabled and the clone URL embeds that scoped token.
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ login: 'edx_userLogin', internal: true, vcsAccessToken: undefined } as User));
            localStorageState = RepositoryAuthenticationMethod.Token;

            const othersParticipation = {
                id: 45,
                student: { login: 'some_student' },
                repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-some_student.git',
            } as ProgrammingExerciseStudentParticipation;

            await component.ngOnInit();
            component.isInCourseManagement.set(true);
            fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
            fixture.componentRef.setInput('participations', [othersParticipation]);
            fixture.detectChanges();
            await fixture.whenStable();
            component.onClick();
            fixture.detectChanges();

            expect(component.usesStudentRepositoryStaffToken()).toBe(true);
            expect(getRepoTokenSpy).toHaveBeenCalledWith(99, 'USER', undefined, 45);
            expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();
            expect(component.repositoryAccessToken()).toEqual(repoToken);
            expect(component.copyEnabled()).toBe(true);
            const url = component.getHttpOrSshRepositoryUri(false);
            expect(url).toContain(`:${repoToken}@`);
            expect(url).not.toContain('undefined');
        });

        it('should mint the student token from a participationId input when no participation object is passed (staff tables)', async () => {
            // The scores / participations / feedback tables render the code button with only repositoryUri + exerciseId +
            // participationId (no participations array). The scoped USER token must still be minted so the button is never disabled.
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ login: 'edx_userLogin', internal: true, vcsAccessToken: undefined } as User));
            localStorageState = RepositoryAuthenticationMethod.Token;
            fixture.componentRef.setInput('exerciseId', 55);
            fixture.componentRef.setInput('participationId', 66);
            fixture.componentRef.setInput('repositoryUri', 'https://artemis.tum.de/git/COURSE/some-student.git');
            await component.ngOnInit();
            component.isInCourseManagement.set(true);
            fixture.detectChanges();
            component.onClick();
            fixture.detectChanges();

            expect(component.usesStudentRepositoryStaffToken()).toBe(true);
            expect(getRepoTokenSpy).toHaveBeenCalledWith(55, 'USER', undefined, 66);
            expect(component.repositoryAccessToken()).toEqual(repoToken);
            expect(component.copyEnabled()).toBe(true);
            const url = component.getHttpOrSshRepositoryUri(false);
            expect(url).toContain(`:${repoToken}@`);
            expect(url).not.toContain('undefined');
        });

        it('should create the repository-scoped student token on demand when none exists yet (404)', async () => {
            getRepoTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            localStorageState = RepositoryAuthenticationMethod.Token;

            const othersParticipation = {
                id: 46,
                student: { login: 'some_student' },
                repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-some_student.git',
            } as ProgrammingExerciseStudentParticipation;

            await component.ngOnInit();
            component.isInCourseManagement.set(true);
            fixture.componentRef.setInput('exercise', { id: 99 } as ProgrammingExercise);
            fixture.componentRef.setInput('participations', [othersParticipation]);
            fixture.detectChanges();
            await fixture.whenStable();
            component.onClick();
            fixture.detectChanges();

            expect(createRepoTokenSpy).toHaveBeenCalledWith(99, 'USER', undefined, 46);
            expect(component.repositoryAccessToken()).toEqual(repoToken);
        });
    });

    it('should create new vcsAccessToken when it does not exist', async () => {
        createVcsAccessTokenSpy = vi.spyOn(accountService, 'createVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
        getVcsAccessTokenSpy = vi.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not found' })));

        participation.id = 1;
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();
        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).toHaveBeenCalled();
    });

    it('should get ssh url (same url for team and individual participation)', async () => {
        participation.repositoryUri = 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git';
        participation.team = {};
        fixture.componentRef.setInput('participations', [participation]);
        component.sshTemplateUrl = 'ssh://git@artemis.tum.de:7999/';
        fixture.changeDetectorRef.detectChanges();
        component.onClick();
        fixture.detectChanges();
        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@artemis.tum.de:7999/git/ITCPLEASE1/itcplease1-exercise.git');

        participation.team = undefined;
        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@artemis.tum.de:7999/git/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get copy the repository uri', async () => {
        participation.repositoryUri = `https://${component.user.login}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`;
        participation.team = {};
        fixture.componentRef.setInput('participations', [participation]);
        component.useHttpsPassword();
        fixture.changeDetectorRef.detectChanges();

        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should insert the correct token in the repository uri', async () => {
        participation.repositoryUri = `https://${component.user.login}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);
        localStorageState = RepositoryAuthenticationMethod.Token;

        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();

        component.onClick();
        fixture.detectChanges();
        // Placeholder is shown
        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:${vcsToken}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);

        // Team participation does not include user name
        fixture.componentRef.setInput('repositoryUri', `https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);
        participation.team = {};

        // Placeholder is shown
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:${vcsToken}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should use the participation token for the users own test run participation in course management', async () => {
        // Exam test runs are served under a course-management URL, but the participation belongs to the current user,
        // so its own participation token (not the personal staff token) must be used and loaded.
        const participationToken = 'vcpat-own-test-run-participation-token';
        getVcsAccessTokenSpy.mockReturnValue(of(new HttpResponse({ body: participationToken })));
        localStorageState = RepositoryAuthenticationMethod.Token;

        const ownTestRunParticipation = {
            id: 42,
            testRun: true,
            student: { login: 'edx_userLogin' },
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
        } as ProgrammingExerciseStudentParticipation;

        await component.ngOnInit();
        component.isInCourseManagement.set(true);
        fixture.componentRef.setInput('participations', [ownTestRunParticipation]);
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();

        expect(component.isInCourseManagement()).toBe(true);
        expect(getVcsAccessTokenSpy).toHaveBeenCalledWith(42);
        expect(ownTestRunParticipation.vcsAccessToken).toEqual(participationToken);
        const url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://edx_userLogin:${participationToken}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git`);
        expect(url).not.toContain('undefined');
    });

    it('should mint a repository-scoped staff token for another users test run participation in course management', async () => {
        // When course staff open another instructor's test run summary, the participation is not owned by them. Instead of the
        // (deprecated) personal staff token, a repository-scoped USER token is minted on demand for that student repository.
        const repoToken = 'vcpat-StaffScopedTestRunTokenStaffScopedTestRun12';
        const getRepoTokenSpy = vi.spyOn(programmingExerciseService, 'getRepositoryVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: repoToken })));
        localStorageState = RepositoryAuthenticationMethod.Token;

        const othersTestRunParticipation = {
            id: 43,
            testRun: true,
            student: { login: 'other_instructor' },
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
        } as ProgrammingExerciseStudentParticipation;

        await component.ngOnInit();
        component.isInCourseManagement.set(true);
        fixture.componentRef.setInput('exercise', { id: 77 } as ProgrammingExercise);
        fixture.componentRef.setInput('participations', [othersTestRunParticipation]);
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();

        // The participation token endpoint must not be used for a repository the staff member does not own.
        expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();
        // A repository-scoped staff token is requested for the student (USER) repository, keyed by the participation.
        expect(getRepoTokenSpy).toHaveBeenCalledWith(77, 'USER', undefined, 43);
        const url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://edx_userLogin:${repoToken}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git`);
        expect(url).not.toContain('undefined');
    });

    it('should omit the token and report to Sentry when a participation token is unexpectedly missing', async () => {
        const captureSpy = vi.spyOn(Sentry, 'captureException').mockImplementation(() => '');
        captureSpy.mockClear();
        // Simulate a participation whose token can neither be fetched (404) nor created (created but empty body); this is
        // the terminal failure state that should be reported (should never happen in practice).
        getVcsAccessTokenSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })));
        createVcsAccessTokenSpy.mockReturnValue(of(new HttpResponse<string>({ body: undefined })));
        localStorageState = RepositoryAuthenticationMethod.Token;

        const participation = {
            id: 7,
            testRun: false,
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
        } as ProgrammingExerciseStudentParticipation;

        await component.ngOnInit();
        fixture.componentRef.setInput('participations', [participation]);
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();

        const url = component.getHttpOrSshRepositoryUri(false);
        // Building the URL again must not report a second time (per-instance guard).
        component.getHttpOrSshRepositoryUri(false);
        expect(createVcsAccessTokenSpy).toHaveBeenCalledWith(7);
        expect(url).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git');
        expect(url).not.toContain('undefined');
        expect(captureSpy).toHaveBeenCalledTimes(1);
    });

    it('should not report to Sentry while a participation token is still loading (no terminal failure yet)', async () => {
        const captureSpy = vi.spyOn(Sentry, 'captureException').mockImplementation(() => '');
        captureSpy.mockClear();
        // The token request never resolves during this test, so the token is merely in flight, not terminally missing.
        getVcsAccessTokenSpy.mockReturnValue(new Subject<HttpResponse<string>>().asObservable());
        localStorageState = RepositoryAuthenticationMethod.Token;

        const participation = {
            id: 8,
            testRun: false,
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
        } as ProgrammingExerciseStudentParticipation;

        await component.ngOnInit();
        fixture.componentRef.setInput('participations', [participation]);
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();

        const url = component.getHttpOrSshRepositoryUri(false);
        // The URL omits the (not-yet-available) token, but no anomaly is reported while it is still loading.
        expect(url).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git');
        expect(url).not.toContain('undefined');
        expect(captureSpy).not.toHaveBeenCalled();
    });

    it('should load the own participation token only after the current user resolves (async ordering)', async () => {
        // Reproduces the real ordering: participations and the course-management context are known before the async
        // identity resolves. The token-loading effect must re-run once the user becomes known.
        let resolveIdentity!: (user: User) => void;
        const identitySpy = vi.spyOn(accountService, 'identity').mockReturnValue(new Promise<User>((resolve) => (resolveIdentity = resolve)));
        localStorageState = RepositoryAuthenticationMethod.Token;

        const freshFixture = TestBed.createComponent(CodeButtonComponent);
        freshFixture.componentRef.setInput('smallButtons', true);
        freshFixture.componentRef.setInput('repositoryUri', '');
        freshFixture.componentRef.setInput('routerLinkForRepositoryView', []);
        freshFixture.componentInstance.isInCourseManagement.set(true);
        freshFixture.componentRef.setInput('participations', [
            {
                id: 42,
                testRun: true,
                student: { login: 'edx_userLogin' },
                repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
            } as ProgrammingExerciseStudentParticipation,
        ]);
        freshFixture.detectChanges();

        // Identity still pending → the owner is unknown → the participation token must not be requested yet.
        expect(identitySpy).toHaveBeenCalled();
        expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();

        resolveIdentity({ login: 'edx_userLogin', internal: true, vcsAccessToken: vcsToken } as User);
        await freshFixture.whenStable();
        freshFixture.detectChanges();

        // Now that the user is known, the effect re-runs and loads the own participation token.
        expect(getVcsAccessTokenSpy).toHaveBeenCalledWith(42);
        freshFixture.destroy();
    });

    it('should not report to Sentry while a repository-scoped staff token for a student repository is still loading', async () => {
        const captureSpy = vi.spyOn(Sentry, 'captureException').mockImplementation(() => '');
        captureSpy.mockClear();
        // Course staff browsing another participant's repository: a repository-scoped token is minted on demand. While it is
        // still loading, the URL must omit the token (never contain "undefined"), and this transient state must not be reported.
        const tokenSubject = new Subject<HttpResponse<string>>();
        vi.spyOn(programmingExerciseService, 'getRepositoryVcsAccessToken').mockReturnValue(tokenSubject.asObservable());
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ login: 'edx_userLogin', internal: true, vcsAccessToken: undefined }));
        localStorageState = RepositoryAuthenticationMethod.Token;

        const othersParticipation = {
            id: 44,
            student: { login: 'some_student' },
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
        } as ProgrammingExerciseStudentParticipation;

        await component.ngOnInit();
        component.isInCourseManagement.set(true);
        fixture.componentRef.setInput('exercise', { id: 88 } as ProgrammingExercise);
        fixture.componentRef.setInput('participations', [othersParticipation]);
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();
        fixture.detectChanges();

        const url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git');
        expect(url).not.toContain('undefined');
        expect(captureSpy).not.toHaveBeenCalled();
    });

    it('should add the user login and token to the URL', async () => {
        participation.repositoryUri = `https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);
        component.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.Token);
        fixture.changeDetectorRef.detectChanges();

        const url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should return http url if ssh is selected but http is forced', async () => {
        participation.repositoryUri = `https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);
        component.sshTemplateUrl = 'ssh://git@artemis.tum.de:7999/';

        component.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.SSH);
        fixture.changeDetectorRef.detectChanges();

        const url = component.getHttpOrSshRepositoryUri(false, false, true);
        expect(url).toBe(`https://${component.user.login}@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should handle multiple participations', async () => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git',
            testRun: false,
        };
        const participation2: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-practice.git',
            testRun: true,
        };
        fixture.componentRef.setInput('participations', [participation1, participation2]);
        component.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.Password);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.activeParticipation()).toEqual(participation1);
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.git');
        expect(component.clonedHeadline()).toBe('artemisApp.exerciseActions.cloneRatedRepository');

        fixture.componentRef.setInput('isPractice', true);
        fixture.detectChanges();

        expect(component.activeParticipation()).toEqual(participation2);
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-practice.git');
        expect(component.clonedHeadline()).toBe('artemisApp.exerciseActions.clonePracticeRepository');
    });

    it('should handle no participation', () => {
        fixture.componentRef.setInput('repositoryUri', 'https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.solution.git');
        fixture.componentRef.setInput('participations', []);
        component.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.Password);
        fixture.changeDetectorRef.detectChanges();

        expect(component.isTeamParticipation()).toBeFalsy();
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://edx_userLogin@artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise.solution.git');
    });

    it('should set wasCopied to true and back to false after 3 seconds on successful copy', () => {
        fixture.detectChanges();
        vi.useFakeTimers();
        component.onCopyFinished(true);
        expect(component.wasCopied()).toBeTruthy();
        vi.advanceTimersByTime(3000);
        expect(component.wasCopied()).toBeFalsy();
        vi.useRealTimers();
    });

    it('should not change wasCopied if copy is unsuccessful', () => {
        fixture.detectChanges();
        component.onCopyFinished(false);
        fixture.detectChanges();
        expect(component.wasCopied()).toBeFalsy();
    });

    it('should fetch and store ssh preference', () => {
        participation.repositoryUri = `https://artemis.tum.de/git/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);

        component.sshEnabled = true;
        component.sshTemplateUrl = 'ssh://git@artemis.tum.de:7999/';
        component.authenticationMechanisms.set([RepositoryAuthenticationMethod.Token, RepositoryAuthenticationMethod.SSH, RepositoryAuthenticationMethod.Password]);

        fixture.changeDetectorRef.detectChanges();

        expect(component.useSsh()).toBeFalsy();

        fixture.debugElement.query(By.css('.code-button')).nativeElement.click();
        fixture.detectChanges();

        const useSSHButton = fixture.debugElement.query(By.css('#useSSHButton'));
        expect(useSSHButton).not.toBeNull();
        useSSHButton.nativeElement.click();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(2, 'code-button-state', 'ssh');
        expect(component.useSsh()).toBeTruthy();

        const useHTTPSButton = fixture.debugElement.query(By.css('#useHTTPSButton'));
        expect(useHTTPSButton).not.toBeNull();
        useHTTPSButton.nativeElement.click();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(3, 'code-button-state', 'password');
        expect(component.useSsh()).toBeFalsy();

        const useHTTPSWithTokenButton = fixture.debugElement.query(By.css('#useHTTPSWithTokenButton'));
        expect(useHTTPSWithTokenButton).not.toBeNull();
        useHTTPSWithTokenButton.nativeElement.click();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(4, 'code-button-state', 'token');
        expect(component.useSsh()).toBeFalsy();
        expect(component.useToken()).toBeTruthy();
    });

    it.each([
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: dayjs().subtract(1, 'hour') } as Exercise,
            false,
            2,
        ],
        [[{ id: 1, testRun: true }], { dueDate: dayjs().subtract(1, 'hour') } as Exercise, false, 1],
        [[{ id: 2, testRun: false }], { dueDate: dayjs().subtract(1, 'hour') } as Exercise, false, 2],
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: dayjs().add(1, 'hour') } as Exercise,
            false,
            2,
        ],
        [[{ id: 2, testRun: false }], { dueDate: dayjs().add(1, 'hour') } as Exercise, false, 2],
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: undefined } as Exercise,
            false,
            2,
        ],
        [[{ id: 2, testRun: false }], { dueDate: undefined } as Exercise, false, 2],
        [[{ id: 1, testRun: true }], { exerciseGroup: {} } as Exercise, false, 1],
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: dayjs().subtract(1, 'hour') } as Exercise,
            true,
            1,
        ],
    ])(
        'should correctly choose active participation',
        async (participations: ProgrammingExerciseStudentParticipation[], exercise: Exercise, isPractice: boolean, expected: number) => {
            fixture.componentRef.setInput('participations', participations);
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('isPractice', isPractice);

            fixture.detectChanges();

            expect(component.activeParticipation()?.id).toBe(expected);
        },
    );

    it.each([
        [
            'start theia button should be visible when module feature is active and theia is configured',
            {
                activeModuleFeatures: [MODULE_FEATURE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            true,
        ],
        [
            'start theia button should not be visible when module feature is active but theia is ill-configured',
            {
                activeModuleFeatures: [MODULE_FEATURE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: undefined,
            },
            false,
        ],
        [
            'start theia button should not be visible when module feature is active but onlineIde is not activated',
            {
                activeModuleFeatures: [MODULE_FEATURE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: false,
            },
            {
                theiaImage: 'this-is-an-old-image',
            },
            false,
        ],
        [
            'start theia button should not be visible when module feature is active but url is not set',
            {
                activeModuleFeatures: [MODULE_FEATURE_THEIA],
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            false,
        ],
        [
            'start theia button should not be visible when module feature is not active but url is set',
            {
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            false,
        ],
    ])('%s', async (description, profileInfo, programmingExercise, theiaConfig, expectedVisibility) => {
        const getProfileInfoStub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(profileInfo as ProfileInfo);

        const getTheiaConfigStub = vi.spyOn(programmingExerciseService, 'getTheiaConfig');
        getTheiaConfigStub.mockReturnValue(of(theiaConfig as ProgrammingExerciseTheiaConfig));

        // Expand the programmingExercise by given properties
        fixture.componentRef.setInput('exercise', {
            ...programmingExercise,
        } as any);

        await component.ngOnInit();

        expect(component.theiaEnabled()).toBe(expectedVisibility);
    });

    it('should include the correct data in the form submission when startOnlineIDE is called', async () => {
        const windowOpenSpy = vi.spyOn(window, 'open').mockReturnValue({ name: '' } as any);
        const documentAppendChildSpy = vi.spyOn(document.body, 'appendChild');
        const documentRemoveChildSpy = vi.spyOn(document.body, 'removeChild');
        const formSubmitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {});

        const getToolTokenSpy = vi.spyOn(accountService, 'getToolToken').mockReturnValue(of('token'));

        fixture.componentRef.setInput('exercise', { buildConfig: { theiaImage: 'theia-image' } } as any);
        fixture.componentRef.setInput('participations', [{ repositoryUri: 'https://repo.uri', vcsAccessToken: 'token' } as any]);

        component.theiaPortalURL = 'https://theia.portal.url';

        fixture.detectChanges();

        await component.startOnlineIDE();
        expect(getToolTokenSpy).toHaveBeenCalledOnce();
        expect(windowOpenSpy).toHaveBeenCalledExactlyOnceWith('', '_blank');
        expect(documentAppendChildSpy).toHaveBeenCalledOnce();
        expect(formSubmitSpy).toHaveBeenCalledOnce();

        const form = documentAppendChildSpy.mock.calls[0]?.[0] as HTMLFormElement;
        if (!form) {
            throw new Error('Form element is undefined');
        }
        expect(form.method.toUpperCase()).toBe('GET');
        expect(form.action).toBe('https://theia.portal.url/');
        expect(form.target).toBe('Theia-IDE');

        const inputs = form.getElementsByTagName('input');
        const data: { [key: string]: string } = {
            appDef: 'theia-image',
            gitUri: 'https://edx_userLogin:token@repo.uri',
            gitToken: 'token',
        };

        expect(Array.from(inputs).find((input) => input.name === 'gitUri')).toBeDefined();

        const gitUriTest = Array.from(inputs).find((input) => input.name === 'gitUri');
        expect(gitUriTest).toBeDefined();
        expect(gitUriTest!.value).toBe(data.gitUri);

        windowOpenSpy.mockRestore();
        documentAppendChildSpy.mockRestore();
        documentRemoveChildSpy.mockRestore();
        formSubmitSpy.mockRestore();
    });

    it('should use token authentication when startOnlineIDE is called with SSH is selected', async () => {
        const windowOpenSpy = vi.spyOn(window, 'open').mockReturnValue({ name: '' } as any);
        const formSubmitSpy = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {});
        const documentAppendChildSpy = vi.spyOn(document.body, 'appendChild');

        const getToolTokenSpy = vi.spyOn(accountService, 'getToolToken').mockReturnValue(of('token'));

        fixture.componentRef.setInput('exercise', { buildConfig: { theiaImage: 'theia-image' } } as any);
        fixture.componentRef.setInput('participations', [{ repositoryUri: 'https://repo.uri', vcsAccessToken: 'token' }]);
        component.theiaPortalURL = 'https://theia.portal.url';

        component.sshTemplateUrl = 'https://ssh.repo.url';
        component.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.SSH);

        fixture.changeDetectorRef.detectChanges();

        await component.startOnlineIDE();
        expect(getToolTokenSpy).toHaveBeenCalledOnce();
        expect(windowOpenSpy).toHaveBeenCalledExactlyOnceWith('', '_blank');
        expect(documentAppendChildSpy).toHaveBeenCalledOnce();
        expect(formSubmitSpy).toHaveBeenCalledOnce();

        const form = documentAppendChildSpy.mock.calls[0]?.[0] as HTMLFormElement;
        expect(form).toBeDefined();

        const inputs = form.getElementsByTagName('input');

        const gitUriTest = Array.from(inputs).find((input) => input.name === 'gitUri');
        expect(gitUriTest).toBeDefined();
        expect(gitUriTest!.value).toBe('https://edx_userLogin:token@repo.uri');

        windowOpenSpy.mockRestore();
        documentAppendChildSpy.mockRestore();
        formSubmitSpy.mockRestore();
    });

    function stubServices() {
        const identityStub = vi.spyOn(accountService, 'identity');
        identityStub.mockReturnValue(
            Promise.resolve({
                login: 'edx_userLogin',
                internal: true,
                vcsAccessToken: vcsToken,
            }),
        );

        const getProfileInfoStub = vi.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(info);
    }
});
