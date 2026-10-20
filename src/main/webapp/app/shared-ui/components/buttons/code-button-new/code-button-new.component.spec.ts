import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
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
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { Subject, of, throwError } from 'rxjs';
import { Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CodeButtonNewComponent } from './code-button-new.component';

describe('CodeButtonNewComponent', () => {
    let fixture: ComponentFixture<CodeButtonNewComponent>;
    let component: CodeButtonNewComponent;
    let accountService: Mocked<AccountService>;
    let profileService: Mocked<ProfileService>;
    let programmingExerciseService: Mocked<ProgrammingExerciseService>;
    let ideSettingsService: Mocked<IdeSettingsService>;
    let sshUserSettingsService: Mocked<SshUserSettingsService>;

    const exerciseId = 42;
    const repositoryUri = 'https://vcs.example.com/course/exercise.git';
    const vcsToken = 'vcpat-test-token';
    const theiaPortalURL = 'https://theia.example.com';
    const theiaImage = 'ghcr.io/example/theia:latest';
    const user = Object.assign(new User(undefined, 'student', 'Student', 'Example', 'student@example.com'), { name: 'Student Example' });

    let theiaFeatureActive: boolean;
    let profileInfo: ProfileInfo;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeButtonNewComponent],
            providers: [
                provideRouter([]),
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
                { provide: ProgrammingExerciseService, useValue: { getTheiaConfig: vi.fn() } },
                { provide: IdeSettingsService, useValue: { loadIdePreferences: vi.fn() } },
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

    function createComponent(allowOnlineIde = true, programmingLanguage: ProgrammingLanguage | undefined = ProgrammingLanguage.JAVA, componentRepositoryUri = repositoryUri): void {
        fixture = TestBed.createComponent(CodeButtonNewComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', exerciseId);
        fixture.componentRef.setInput('repositoryUri', componentRepositoryUri);
        fixture.componentRef.setInput('vcsAccessTokenData', { type: 'participation', participationId: 7, isOwnParticipation: true });
        fixture.componentRef.setInput('repositoryViewLink', ['/courses', 1, 'exercises', exerciseId, 'repository']);
        fixture.componentRef.setInput('allowOnlineIde', allowOnlineIde);
        fixture.componentRef.setInput('programmingLanguage', programmingLanguage);
        fixture.detectChanges();
    }

    describe('authentication method data', () => {
        const sshCloneURLTemplate = 'ssh://git@vcs.example.com:7999/';

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
