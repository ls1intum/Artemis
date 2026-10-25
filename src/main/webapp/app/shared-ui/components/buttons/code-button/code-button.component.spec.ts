import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiPopoverComponent } from '@tumaet/ui-angular';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import { User } from 'app/account/user/user.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CodeButtonComponent, RepositoryAuthenticationMethod, type VcsAccessTokenSourceInfo } from './code-button.component';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
describe('CodeButtonComponent', () => {
    let fixture: ComponentFixture<CodeButtonComponent>;
    let component: CodeButtonComponent;
    let accountService: Mocked<AccountService>;
    let profileService: Mocked<ProfileService>;
    let programmingExerciseService: Mocked<ProgrammingExerciseService>;
    let ideSettingsService: Mocked<IdeSettingsService>;
    let sshUserSettingsService: Mocked<SshUserSettingsService>;
    let mediumBreakpoint: BehaviorSubject<BreakpointState>;
    let extraLargeBreakpoint: BehaviorSubject<BreakpointState>;

    const exerciseId = 42;
    const repositoryUri = 'https://vcs.example.com/course/exercise.git';
    const vcsToken = 'vcpat-test-token';
    const theiaPortalURL = 'https://theia.example.com';
    const theiaImage = 'ghcr.io/example/theia:latest';
    const user = Object.assign(new User(undefined, 'student', 'Student', 'Example', 'student@example.com'), { name: 'Student Example' });

    let theiaFeatureActive: boolean;
    let profileInfo: ProfileInfo;

    beforeEach(async () => {
        mediumBreakpoint = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
        extraLargeBreakpoint = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
        await TestBed.configureTestingModule({
            imports: [CodeButtonComponent],
            providers: [
                provideRouter([{ path: 'course-management', component: CodeButtonComponent }]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: AccountService,
                    useValue: {
                        identity: vi.fn(),
                        getVcsAccessToken: vi.fn(),
                        createVcsAccessToken: vi.fn(),
                    },
                },
                {
                    provide: ProfileService,
                    useValue: {
                        getProfileInfo: vi.fn(),
                        isModuleFeatureActive: vi.fn(),
                    },
                },
                { provide: SshUserSettingsService, useValue: { getCachedSshKeys: vi.fn() } },
                {
                    provide: ProgrammingExerciseService,
                    useValue: {
                        getTheiaConfig: vi.fn(),
                        getRepositoryVcsAccessToken: vi.fn(),
                        createRepositoryVcsAccessToken: vi.fn(),
                    },
                },
                { provide: IdeSettingsService, useValue: { loadIdePreferences: vi.fn() } },
                {
                    provide: BreakpointObserver,
                    useValue: {
                        observe: vi.fn((breakpoint: string) => (breakpoint === '(min-width: 768px)' ? mediumBreakpoint : extraLargeBreakpoint)),
                        isMatched: vi.fn(() => false),
                    },
                },
            ],
        }).compileComponents();

        accountService = TestBed.inject(AccountService) as Mocked<AccountService>;
        profileService = TestBed.inject(ProfileService) as Mocked<ProfileService>;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService) as Mocked<ProgrammingExerciseService>;
        ideSettingsService = TestBed.inject(IdeSettingsService) as Mocked<IdeSettingsService>;
        sshUserSettingsService = TestBed.inject(SshUserSettingsService) as Mocked<SshUserSettingsService>;

        theiaFeatureActive = true;
        profileInfo = {
            repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.Token],
            theiaPortalURL,
        } as ProfileInfo;
        profileService.getProfileInfo.mockImplementation(() => profileInfo);
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_THEIA && theiaFeatureActive);
        accountService.identity.mockResolvedValue(user);
        accountService.getVcsAccessToken.mockReturnValue(of(new HttpResponse({ body: vcsToken })));
        ideSettingsService.loadIdePreferences.mockResolvedValue(new Map());
        programmingExerciseService.getTheiaConfig.mockReturnValue(of({ theiaImage }));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function createComponent(
        allowOnlineIde = true,
        programmingLanguage: ProgrammingLanguage | undefined = ProgrammingLanguage.JAVA,
        componentRepositoryUri = repositoryUri,
        vcsAccessTokenData: VcsAccessTokenSourceInfo = { returnedTokenType: 'participation-token', participationId: 7 },
        openPopover = true,
    ): void {
        fixture = TestBed.createComponent(CodeButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', exerciseId);
        fixture.componentRef.setInput('repositoryUri', componentRepositoryUri);
        fixture.componentRef.setInput('vcsAccessTokenData', vcsAccessTokenData);
        fixture.componentRef.setInput('repositoryViewLink', ['/courses', 1, 'exercises', exerciseId, 'repository']);
        fixture.componentRef.setInput('allowOnlineIde', allowOnlineIde);
        fixture.componentRef.setInput('programmingLanguage', programmingLanguage);
        fixture.detectChanges();
        if (openPopover) {
            const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
            trigger.click();
            fixture.detectChanges();
        }
    }

    it('should defer resource loading until the popover trigger is clicked', async () => {
        createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, { returnedTokenType: 'participation-token', participationId: 7 }, false);

        expect(accountService.identity).not.toHaveBeenCalled();
        expect(accountService.getVcsAccessToken).not.toHaveBeenCalled();
        expect(ideSettingsService.loadIdePreferences).not.toHaveBeenCalled();
        expect(component['loadingState']()).toEqual({ status: 'idle' });

        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        trigger.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(accountService.identity).toHaveBeenCalledOnce();
        expect(accountService.getVcsAccessToken).toHaveBeenCalledExactlyOnceWith(7);
        expect(ideSettingsService.loadIdePreferences).toHaveBeenCalledOnce();
    });

    it('should reset alternative options when the popover closes', async () => {
        createComponent(false);
        await fixture.whenStable();
        const popover = fixture.debugElement.query(By.directive(TumAetUiPopoverComponent)).componentInstance as TumAetUiPopoverComponent;

        component['showAlternatives'].set(true);
        expect(popover.isOpen()).toBe(true);
        expect(component['showAlternatives']()).toBe(true);

        popover.close();
        expect(popover.isOpen()).toBe(false);
        expect(component['showAlternatives']()).toBe(false);
    });

    it('should disable only trigger if disabled input is set', async () => {
        createComponent(false);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        expect(trigger.disabled).toBe(true);

        fixture.componentRef.setInput('disabled', false);
        fixture.detectChanges();
        expect(trigger.disabled).toBe(false);
    });

    it.each([
        { size: 'small', expectedClass: 'tumaet:text-sm' },
        { size: 'default', expectedClass: 'tumaet:text-base' },
    ] as const)('should render the $size trigger size', ({ size, expectedClass }) => {
        createComponent(false);
        fixture.componentRef.setInput('size', size);
        fixture.detectChanges();

        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        expect(trigger.classList).toContain(expectedClass);
    });

    it('should show the label by default', () => {
        createComponent(false);

        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        expect(trigger.textContent).toContain('artemisApp.exerciseActions.code');
    });

    it('should hide the label below the medium breakpoint', () => {
        createComponent(false);
        fixture.componentRef.setInput('hideLabelMobile', true);
        fixture.detectChanges();
        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        expect(trigger.textContent).not.toContain('artemisApp.exerciseActions.code');

        mediumBreakpoint.next({ matches: true, breakpoints: { '(min-width: 768px)': true } });
        fixture.detectChanges();
        expect(trigger.textContent).toContain('artemisApp.exerciseActions.code');
    });

    it('should hide the label until the extra-large breakpoint when configured', () => {
        mediumBreakpoint.next({ matches: true, breakpoints: { '(min-width: 768px)': true } });
        createComponent(false);
        fixture.componentRef.setInput('hideLabelMobile', true);
        fixture.componentRef.setInput('hideLabelBreakpoint', 'xl');
        fixture.detectChanges();
        const trigger = fixture.debugElement.query(By.css('[data-testid="code-button"]')).nativeElement as HTMLButtonElement;
        expect(trigger.textContent).not.toContain('artemisApp.exerciseActions.code');

        extraLargeBreakpoint.next({ matches: true, breakpoints: { '(min-width: 1200px)': true } });
        fixture.detectChanges();
        expect(trigger.textContent).toContain('artemisApp.exerciseActions.code');
    });

    describe('VCS access token', () => {
        const createdToken = 'new-vcpat-test-token';
        const repositoryTokenCases: {
            description: string;
            vcsAccessTokenData: VcsAccessTokenSourceInfo;
            expectedRepositoryType: RepositoryType;
            expectedAuxiliaryRepositoryId: number | undefined;
            expectedParticipationId: number | undefined;
        }[] = [
            {
                description: 'student repository',
                vcsAccessTokenData: { returnedTokenType: 'repository-token', repositoryType: RepositoryType.USER, participationId: 17 },
                expectedRepositoryType: RepositoryType.USER,
                expectedAuxiliaryRepositoryId: undefined,
                expectedParticipationId: 17,
            },
            {
                description: 'base repository',
                vcsAccessTokenData: { returnedTokenType: 'repository-token', repositoryType: RepositoryType.TEMPLATE },
                expectedRepositoryType: RepositoryType.TEMPLATE,
                expectedAuxiliaryRepositoryId: undefined,
                expectedParticipationId: undefined,
            },
            {
                description: 'auxiliary repository',
                vcsAccessTokenData: { returnedTokenType: 'repository-token', repositoryType: RepositoryType.AUXILIARY, auxiliaryRepositoryId: 23 },
                expectedRepositoryType: RepositoryType.AUXILIARY,
                expectedAuxiliaryRepositoryId: 23,
                expectedParticipationId: undefined,
            },
        ];

        it('should load an existing participation token without creating one', async () => {
            createComponent(false);
            await fixture.whenStable();

            expect(accountService.getVcsAccessToken).toHaveBeenCalledExactlyOnceWith(7);
            expect(accountService.createVcsAccessToken).not.toHaveBeenCalled();
            expect(programmingExerciseService.getRepositoryVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [{ authenticationMethod: RepositoryAuthenticationMethod.Token, url: `https://student:${vcsToken}@vcs.example.com/course/exercise.git` }],
            });
        });

        it('should create a participation token when none exists', async () => {
            accountService.getVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            accountService.createVcsAccessToken.mockReturnValue(of(new HttpResponse({ body: createdToken })));
            createComponent(false);
            await fixture.whenStable();

            expect(accountService.getVcsAccessToken).toHaveBeenCalledWith(7);
            expect(accountService.createVcsAccessToken).toHaveBeenCalledWith(7);
            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [
                    { authenticationMethod: RepositoryAuthenticationMethod.Token, url: `https://student:${createdToken}@vcs.example.com/course/exercise.git` },
                ],
            });
        });

        it.each(repositoryTokenCases)(
            'should load an existing token for the $description',
            async ({ vcsAccessTokenData, expectedRepositoryType, expectedAuxiliaryRepositoryId, expectedParticipationId }) => {
                programmingExerciseService.getRepositoryVcsAccessToken.mockReturnValue(of(new HttpResponse({ body: vcsToken })));
                createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, vcsAccessTokenData);
                await fixture.whenStable();

                expect(programmingExerciseService.getRepositoryVcsAccessToken).toHaveBeenCalledWith(
                    exerciseId,
                    expectedRepositoryType,
                    expectedAuxiliaryRepositoryId,
                    expectedParticipationId,
                );
                expect(programmingExerciseService.createRepositoryVcsAccessToken).not.toHaveBeenCalled();
                expect(accountService.getVcsAccessToken).not.toHaveBeenCalled();
                expect(accountService.createVcsAccessToken).not.toHaveBeenCalled();
                expect(component['loadingState']()).toMatchObject({
                    status: 'complete',
                    authenticationMethodData: [
                        { authenticationMethod: RepositoryAuthenticationMethod.Token, url: `https://student:${vcsToken}@vcs.example.com/course/exercise.git` },
                    ],
                });
            },
        );

        it('should create a repository token when none exists', async () => {
            const vcsAccessTokenData: VcsAccessTokenSourceInfo = { returnedTokenType: 'repository-token', repositoryType: RepositoryType.SOLUTION };
            programmingExerciseService.getRepositoryVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            programmingExerciseService.createRepositoryVcsAccessToken.mockReturnValue(of(new HttpResponse({ body: createdToken })));
            createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, vcsAccessTokenData);
            await fixture.whenStable();

            expect(programmingExerciseService.getRepositoryVcsAccessToken).toHaveBeenCalledWith(exerciseId, RepositoryType.SOLUTION, undefined, undefined);
            expect(programmingExerciseService.createRepositoryVcsAccessToken).toHaveBeenCalledWith(exerciseId, RepositoryType.SOLUTION, undefined, undefined);
            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [
                    { authenticationMethod: RepositoryAuthenticationMethod.Token, url: `https://student:${createdToken}@vcs.example.com/course/exercise.git` },
                ],
            });
        });

        it('should create a student repository token when none exists', async () => {
            programmingExerciseService.getRepositoryVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            programmingExerciseService.createRepositoryVcsAccessToken.mockReturnValue(of(new HttpResponse({ body: createdToken })));
            createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, {
                returnedTokenType: 'repository-token',
                repositoryType: RepositoryType.USER,
                participationId: 17,
            });
            await fixture.whenStable();

            expect(programmingExerciseService.getRepositoryVcsAccessToken).toHaveBeenCalledExactlyOnceWith(exerciseId, RepositoryType.USER, undefined, 17);
            expect(programmingExerciseService.createRepositoryVcsAccessToken).toHaveBeenCalledExactlyOnceWith(exerciseId, RepositoryType.USER, undefined, 17);
            expect(accountService.getVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [
                    { authenticationMethod: RepositoryAuthenticationMethod.Token, url: `https://student:${createdToken}@vcs.example.com/course/exercise.git` },
                ],
            });
        });

        it('should expose an error when a successful token response has no token in its body', async () => {
            accountService.getVcsAccessToken.mockReturnValue(of(new HttpResponse<string>({ body: null })));
            createComponent(false);
            await fixture.whenStable();

            expect(accountService.createVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when a successful token creation response has no token in its body', async () => {
            accountService.getVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            accountService.createVcsAccessToken.mockReturnValue(of(new HttpResponse<string>({ body: null })));
            createComponent(false);
            await fixture.whenStable();

            expect(accountService.createVcsAccessToken).toHaveBeenCalledExactlyOnceWith(7);
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error without creating a token when loading fails with a non-404 response', async () => {
            accountService.getVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            createComponent(false);
            await fixture.whenStable();

            expect(accountService.createVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error without creating a repository token when loading fails with a non-404 response', async () => {
            programmingExerciseService.getRepositoryVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, { returnedTokenType: 'repository-token', repositoryType: RepositoryType.TESTS });
            await fixture.whenStable();

            expect(programmingExerciseService.createRepositoryVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when creating a repository token fails', async () => {
            programmingExerciseService.getRepositoryVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            programmingExerciseService.createRepositoryVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, { returnedTokenType: 'repository-token', repositoryType: RepositoryType.SOLUTION });
            await fixture.whenStable();

            expect(programmingExerciseService.createRepositoryVcsAccessToken).toHaveBeenCalledExactlyOnceWith(exerciseId, RepositoryType.SOLUTION, undefined, undefined);
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should load a participation token', async () => {
            createComponent(false, ProgrammingLanguage.JAVA, repositoryUri, { returnedTokenType: 'participation-token', participationId: 23 });
            await fixture.whenStable();

            expect(accountService.getVcsAccessToken).toHaveBeenCalledExactlyOnceWith(23);
            expect(programmingExerciseService.getRepositoryVcsAccessToken).not.toHaveBeenCalled();
            expect(component['loadingState']()).toMatchObject({ status: 'complete' });
        });
    });

    describe('authentication method data', () => {
        const sshCloneURLTemplate = 'ssh://git@vcs.example.com:7999/';

        it('should expose an error when no user is logged in', async () => {
            accountService.identity.mockResolvedValue(undefined);
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when the user has no login', async () => {
            accountService.identity.mockResolvedValue(Object.assign(new User(), user, { login: undefined }));
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose all supported authentication methods in profile order', async () => {
            profileInfo = {
                ...profileInfo,
                repositoryAuthenticationMechanisms: [
                    RepositoryAuthenticationMethod.SSH,
                    'unsupported',
                    RepositoryAuthenticationMethod.Token,
                    RepositoryAuthenticationMethod.Password,
                ],
                sshCloneURLTemplate,
            };
            sshUserSettingsService.getCachedSshKeys.mockResolvedValue([]);
            createComponent(false, ProgrammingLanguage.JAVA, 'https://old-user@vcs.example.com/course/exercise.git');
            await fixture.whenStable();

            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [
                    {
                        authenticationMethod: RepositoryAuthenticationMethod.SSH,
                        url: 'ssh://git@vcs.example.com:7999/course/exercise.git',
                        sshKeyState: { status: 'missing' },
                    },
                    {
                        authenticationMethod: RepositoryAuthenticationMethod.Token,
                        url: `https://student:${vcsToken}@vcs.example.com/course/exercise.git`,
                    },
                    {
                        authenticationMethod: RepositoryAuthenticationMethod.Password,
                        url: 'https://student@vcs.example.com/course/exercise.git',
                    },
                ],
            });
        });

        it('should show SSH authentication when Token authentication is not configured', async () => {
            profileInfo = {
                ...profileInfo,
                repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.SSH],
                sshCloneURLTemplate,
            };
            sshUserSettingsService.getCachedSshKeys.mockResolvedValue([]);
            accountService.getVcsAccessToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            createComponent(false);
            await fixture.whenStable();

            const repositoryUrl = document.querySelector('jhi-code-button-popover-content code')?.textContent;
            expect(repositoryUrl).toContain('ssh://git@vcs.example.com:7999/course/exercise.git');
        });

        it.each([
            {
                description: 'none of the configured keys are expired',
                sshKeys: [new UserSshPublicKey(), Object.assign(new UserSshPublicKey(), { expiryDate: dayjs().add(1, 'day') })],
                hasExpiredKeys: false,
            },
            {
                description: 'a configured key is expired',
                sshKeys: [Object.assign(new UserSshPublicKey(), { expiryDate: dayjs().subtract(1, 'day') })],
                hasExpiredKeys: true,
            },
        ])('should expose configured SSH keys when $description', async ({ sshKeys, hasExpiredKeys }) => {
            profileInfo = {
                ...profileInfo,
                repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.SSH],
                sshCloneURLTemplate,
            };
            sshUserSettingsService.getCachedSshKeys.mockResolvedValue(sshKeys);
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                authenticationMethodData: [
                    {
                        authenticationMethod: RepositoryAuthenticationMethod.SSH,
                        sshKeyState: { status: 'configured', hasExpiredKeys },
                    },
                ],
            });
        });

        it('should expose an error when no supported authentication method is configured', async () => {
            profileInfo = { ...profileInfo, repositoryAuthenticationMechanisms: ['unsupported'] };
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when the SSH clone URL template is missing', async () => {
            profileInfo = { ...profileInfo, repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.SSH] };
            Reflect.deleteProperty(profileInfo, 'sshCloneURLTemplate');
            sshUserSettingsService.getCachedSshKeys.mockResolvedValue([]);
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when loading SSH keys fails', async () => {
            profileInfo = {
                ...profileInfo,
                repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.SSH],
                sshCloneURLTemplate,
            };
            sshUserSettingsService.getCachedSshKeys.mockRejectedValue(new Error('SSH key request failed'));
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when loading SSH keys returns no result', async () => {
            profileInfo = {
                ...profileInfo,
                repositoryAuthenticationMechanisms: [RepositoryAuthenticationMethod.SSH],
                sshCloneURLTemplate,
            };
            sshUserSettingsService.getCachedSshKeys.mockResolvedValue(undefined);
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });
    });

    describe('preferred IDE', () => {
        const intellij: Ide = { name: 'IntelliJ IDEA', deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}' };
        const eclipse: Ide = { name: 'Eclipse', deepLink: 'eclipse://clone?url={cloneUrl}' };
        const vsCodeFallback: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        it.each([
            {
                description: 'the programming language has a preference',
                programmingLanguage: ProgrammingLanguage.JAVA,
                preferences: new Map([[ProgrammingLanguage.JAVA, intellij]]),
                expectedIde: intellij,
            },
            {
                description: 'the programming language has no preference',
                programmingLanguage: ProgrammingLanguage.JAVA,
                preferences: new Map([[ProgrammingLanguage.EMPTY, eclipse]]),
                expectedIde: eclipse,
            },
            {
                description: 'no programming language is supplied',
                programmingLanguage: undefined,
                preferences: new Map([[ProgrammingLanguage.EMPTY, eclipse]]),
                expectedIde: eclipse,
            },
            {
                description: 'no matching or generic preference exists',
                programmingLanguage: ProgrammingLanguage.JAVA,
                preferences: new Map<ProgrammingLanguage, Ide>(),
                expectedIde: vsCodeFallback,
            },
        ])('should expose the expected IDE when $description', async ({ programmingLanguage, preferences, expectedIde }) => {
            ideSettingsService.loadIdePreferences.mockResolvedValue(preferences);
            createComponent(false, programmingLanguage);
            await fixture.whenStable();

            expect(ideSettingsService.loadIdePreferences).toHaveBeenCalledOnce();
            expect(component['loadingState']()).toMatchObject({ status: 'complete', preferredIde: expectedIde });
        });

        it('should expose an error when loading IDE preferences fails', async () => {
            ideSettingsService.loadIdePreferences.mockRejectedValue(new Error('IDE preferences request failed'));
            createComponent(false);
            await fixture.whenStable();

            expect(component['loadingState']()).toEqual({ status: 'error' });
        });
    });

    describe('Theia link data', () => {
        it('should expose loading and then complete with Theia link data from the resolved dependencies', async () => {
            const theiaConfigResponse = new Subject<{ theiaImage?: string }>();
            programmingExerciseService.getTheiaConfig.mockReturnValue(theiaConfigResponse);
            createComponent();

            await vi.waitFor(() => expect(programmingExerciseService.getTheiaConfig).toHaveBeenCalledWith(exerciseId));
            expect(component['loadingState']()).toEqual({ status: 'loading' });

            theiaConfigResponse.next({ theiaImage });
            theiaConfigResponse.complete();
            await fixture.whenStable();

            expect(component['loadingState']()).toMatchObject({
                status: 'complete',
                theiaLinkData: {
                    theiaPortalURL,
                    theiaImage,
                    repositoryUrl: `https://student:${vcsToken}@vcs.example.com/course/exercise.git`,
                    userName: user.name,
                    userEmail: user.email,
                },
            });
        });

        it.each([
            { description: 'online IDE use is disallowed', allowOnlineIde: false, featureActive: true },
            { description: 'the Theia module feature is inactive', allowOnlineIde: true, featureActive: false },
        ])('should complete without Theia link data when $description', async ({ allowOnlineIde, featureActive }) => {
            theiaFeatureActive = featureActive;
            createComponent(allowOnlineIde);
            await fixture.whenStable();

            expect(programmingExerciseService.getTheiaConfig).not.toHaveBeenCalled();
            expect(component['loadingState']()).toMatchObject({ status: 'complete', theiaLinkData: undefined });
        });

        it.each([
            { description: 'the profile has no Theia portal URL', profileOverrides: { theiaPortalURL: undefined }, userOverrides: {} },
            { description: 'the user has no name', profileOverrides: {}, userOverrides: { name: undefined } },
            { description: 'the user has no email address', profileOverrides: {}, userOverrides: { email: undefined } },
        ])('should expose an error when $description', async ({ profileOverrides, userOverrides }) => {
            profileInfo = { ...profileInfo, ...profileOverrides };
            accountService.identity.mockResolvedValue(Object.assign(new User(), user, userOverrides));
            createComponent();
            await fixture.whenStable();

            expect(programmingExerciseService.getTheiaConfig).not.toHaveBeenCalled();
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when loading the Theia configuration fails', async () => {
            programmingExerciseService.getTheiaConfig.mockReturnValue(throwError(() => new Error('Theia configuration request failed')));
            createComponent();
            await fixture.whenStable();

            expect(programmingExerciseService.getTheiaConfig).toHaveBeenCalledWith(exerciseId);
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });

        it('should expose an error when the Theia configuration has no image', async () => {
            programmingExerciseService.getTheiaConfig.mockReturnValue(of({}));
            createComponent();
            await fixture.whenStable();

            expect(programmingExerciseService.getTheiaConfig).toHaveBeenCalledWith(exerciseId);
            expect(component['loadingState']()).toEqual({ status: 'error' });
        });
    });
});
