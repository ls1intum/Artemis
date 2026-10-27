import { CdkCopyToClipboard, Clipboard } from '@angular/cdk/clipboard';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiMenuTriggerDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { RepositoryAuthenticationMethod } from './code-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Mocked, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, TheiaLinkData } from './code-button-popover-content.component';

describe('CodeButtonPopoverContentComponent', () => {
    let fixture: ComponentFixture<CodeButtonPopoverContentComponent>;
    let localStorageService: Mocked<LocalStorageService>;
    let externalCloningService: Mocked<ExternalCloningService>;
    let sanitizer: Mocked<DomSanitizer>;
    let profileService: Mocked<ProfileService>;
    let theiaService: Mocked<TheiaService>;
    let clipboard: Mocked<Clipboard>;

    const repositoryViewLink = ['/courses', 1, 'exercises', 2, 'repository'];
    const versionControlUrl = 'https://vcs.example.com';
    const tokenUrl = 'https://student:secret-token@vcs.example.com/course/exercise.git';
    const maskedTokenUrl = 'https://student:**********@vcs.example.com/course/exercise.git';
    const passwordUrl = 'https://student@vcs.example.com/course/exercise.git';
    const sshUrl = 'ssh://git@vcs.example.com:7999/course/exercise.git';
    const preferredIde: Ide = { name: 'IntelliJ IDEA', deepLink: 'jetbrains://idea/checkout?url={cloneUrl}' };
    const theiaLinkData: TheiaLinkData = {
        theiaPortalURL: 'https://theia.example.com',
        theiaImage: 'ghcr.io/example/theia:latest',
        repositoryUrl: tokenUrl,
        userName: 'Student Example',
        userEmail: 'student@example.com',
    };
    const tokenAuthenticationMethodData: AuthenticationMethodData = { authenticationMethod: RepositoryAuthenticationMethod.Token, url: tokenUrl };
    const passwordAuthenticationMethodData: AuthenticationMethodData = { authenticationMethod: RepositoryAuthenticationMethod.Password, url: passwordUrl };
    const sshAuthenticationMethodData: AuthenticationMethodData = {
        authenticationMethod: RepositoryAuthenticationMethod.SSH,
        url: sshUrl,
        sshKeyState: { status: 'configured', hasExpiredKeys: false },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeButtonPopoverContentComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useValue: { retrieve: vi.fn(), store: vi.fn() } },
                { provide: ExternalCloningService, useValue: { buildIdeUrl: vi.fn(), buildSourceTreeUrl: vi.fn() } },
                { provide: DomSanitizer, useValue: { sanitize: vi.fn(), bypassSecurityTrustUrl: vi.fn(), bypassSecurityTrustHtml: vi.fn() } },
                { provide: TheiaService, useValue: { startOnlineIDE: vi.fn() } },
                { provide: ProfileService, useValue: { getProfileInfo: vi.fn() } },
                { provide: Clipboard, useValue: { copy: vi.fn() } },
            ],
        }).compileComponents();

        localStorageService = TestBed.inject(LocalStorageService) as Mocked<LocalStorageService>;
        externalCloningService = TestBed.inject(ExternalCloningService) as Mocked<ExternalCloningService>;
        sanitizer = TestBed.inject(DomSanitizer) as Mocked<DomSanitizer>;
        profileService = TestBed.inject(ProfileService) as Mocked<ProfileService>;
        theiaService = TestBed.inject(TheiaService) as Mocked<TheiaService>;
        clipboard = TestBed.inject(Clipboard) as Mocked<Clipboard>;

        localStorageService.retrieve.mockReturnValue(undefined);
        externalCloningService.buildIdeUrl.mockReturnValue(undefined);
        externalCloningService.buildSourceTreeUrl.mockReturnValue(undefined);
        profileService.getProfileInfo.mockReturnValue({ versionControlUrl } as ProfileInfo);
        clipboard.copy.mockReturnValue(true);
    });

    function renderComponent(authenticationMethodData: NonEmptyArray<AuthenticationMethodData>, showAlternatives = false, currentTheiaLinkData?: TheiaLinkData): void {
        fixture = TestBed.createComponent(CodeButtonPopoverContentComponent);
        fixture.componentRef.setInput('repositoryViewLink', repositoryViewLink);
        fixture.componentRef.setInput('authenticationMethodData', authenticationMethodData);
        fixture.componentRef.setInput('preferredIde', preferredIde);
        fixture.componentRef.setInput('showAlternatives', showAlternatives);
        fixture.componentRef.setInput('theiaLinkData', currentTheiaLinkData);
        fixture.detectChanges();
    }

    function translatedElement(translationKey: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[jhitranslate="${translationKey}"]`);
    }

    function authenticationMenuTrigger(): HTMLButtonElement {
        return fixture.debugElement.query(By.directive(TumAetUiMenuTriggerDirective)).nativeElement;
    }

    function repositoryUrlElement(): HTMLElement {
        return fixture.nativeElement.querySelector('code');
    }

    function copyButton(): HTMLButtonElement {
        return fixture.debugElement.query(By.directive(CdkCopyToClipboard)).nativeElement;
    }

    async function openAuthenticationMenu(): Promise<HTMLElement[]> {
        authenticationMenuTrigger().click();
        fixture.detectChanges();
        await fixture.whenStable();
        return Array.from(document.querySelectorAll<HTMLElement>('[role="menuitemradio"]'));
    }

    function expectSelectedAuthenticationMethod(label: string, repositoryUrl: string): void {
        expect(authenticationMenuTrigger().textContent).toContain(label);
        expect(repositoryUrlElement().textContent.trim()).toBe(repositoryUrl);
    }

    function trustUrl(url: string): SafeUrl {
        const trustedUrl = { toString: () => url } as SafeUrl;
        sanitizer.sanitize.mockReturnValue(url);
        sanitizer.bypassSecurityTrustUrl.mockReturnValue(trustedUrl);
        return trustedUrl;
    }

    describe('authentication method rendering and selection', () => {
        it('should render every authentication method and select one from the menu', async () => {
            renderComponent([passwordAuthenticationMethodData, tokenAuthenticationMethodData, sshAuthenticationMethodData]);
            expectSelectedAuthenticationMethod('HTTPS', passwordUrl);

            const menuItems = await openAuthenticationMenu();
            expect(menuItems.map((item) => item.textContent?.trim())).toEqual(['HTTPS', 'Token', 'SSH']);

            const sshMenuItem = menuItems.find((item) => item.textContent?.trim() === 'SSH');
            expect(sshMenuItem).toBeDefined();
            sshMenuItem!.click();
            fixture.detectChanges();
            await fixture.whenStable();

            expectSelectedAuthenticationMethod('SSH', sshUrl);
            expect(localStorageService.store).toHaveBeenCalledWith('code-button-state', RepositoryAuthenticationMethod.SSH);
            expect(document.querySelector('[role="menu"]')).toBeNull();
        });

        it.each([
            {
                description: 'SSH was stored and remains available',
                storedAuthenticationMethod: RepositoryAuthenticationMethod.SSH,
                authenticationMethodData: [passwordAuthenticationMethodData, tokenAuthenticationMethodData, sshAuthenticationMethodData] as const,
                expectedLabel: 'SSH',
                expectedUrl: sshUrl,
            },
            {
                description: 'password was stored and token authentication is available',
                storedAuthenticationMethod: RepositoryAuthenticationMethod.Password,
                authenticationMethodData: [passwordAuthenticationMethodData, tokenAuthenticationMethodData, sshAuthenticationMethodData] as const,
                expectedLabel: 'Token',
                expectedUrl: maskedTokenUrl,
            },
            {
                description: 'the stored SSH method is unavailable',
                storedAuthenticationMethod: RepositoryAuthenticationMethod.SSH,
                authenticationMethodData: [tokenAuthenticationMethodData, passwordAuthenticationMethodData] as const,
                expectedLabel: 'Token',
                expectedUrl: maskedTokenUrl,
            },
            {
                description: 'password was stored and token authentication is unavailable',
                storedAuthenticationMethod: RepositoryAuthenticationMethod.Password,
                authenticationMethodData: [sshAuthenticationMethodData, passwordAuthenticationMethodData] as const,
                expectedLabel: 'HTTPS',
                expectedUrl: passwordUrl,
            },
            {
                description: 'password was stored but neither password nor token authentication is available',
                storedAuthenticationMethod: RepositoryAuthenticationMethod.Password,
                authenticationMethodData: [sshAuthenticationMethodData] as const,
                expectedLabel: 'SSH',
                expectedUrl: sshUrl,
            },
        ])('should render the correct initial selection when $description', ({ storedAuthenticationMethod, authenticationMethodData, expectedLabel, expectedUrl }) => {
            localStorageService.retrieve.mockReturnValue(storedAuthenticationMethod);

            renderComponent(authenticationMethodData);

            expectSelectedAuthenticationMethod(expectedLabel, expectedUrl);
        });

        it('should reset the rendered selection when the available methods change', async () => {
            renderComponent([tokenAuthenticationMethodData, sshAuthenticationMethodData]);
            const menuItems = await openAuthenticationMenu();
            expect(menuItems.map((item) => item.getAttribute('aria-checked'))).toEqual(['true', 'false']);
            menuItems.find((item) => item.textContent?.trim() === 'SSH')!.click();
            fixture.detectChanges();
            await fixture.whenStable();
            expectSelectedAuthenticationMethod('SSH', sshUrl);
            const updatedMenuItems = await openAuthenticationMenu();
            expect(updatedMenuItems.map((item) => item.getAttribute('aria-checked'))).toEqual(['false', 'true']);

            fixture.componentRef.setInput('authenticationMethodData', [passwordAuthenticationMethodData]);
            fixture.detectChanges();

            expectSelectedAuthenticationMethod('HTTPS', passwordUrl);
        });
    });

    describe('repository URL rendering and copying', () => {
        it('should mask a token in the UI while copying the complete token URL', () => {
            renderComponent([tokenAuthenticationMethodData]);

            expect(repositoryUrlElement().textContent.trim()).toBe(maskedTokenUrl);
            copyButton().click();

            expect(clipboard.copy).toHaveBeenCalledWith(tokenUrl);
        });

        it.each([
            { description: 'password', authenticationMethodData: passwordAuthenticationMethodData, expectedUrl: passwordUrl },
            { description: 'SSH', authenticationMethodData: sshAuthenticationMethodData, expectedUrl: sshUrl },
        ])('should display and copy the unchanged $description URL', ({ authenticationMethodData, expectedUrl }) => {
            renderComponent([authenticationMethodData]);

            expect(repositoryUrlElement().textContent.trim()).toBe(expectedUrl);
            copyButton().click();

            expect(clipboard.copy).toHaveBeenCalledWith(expectedUrl);
        });

        it('should show temporary feedback only after a successful copy', () => {
            vi.useFakeTimers();
            try {
                clipboard.copy.mockReturnValueOnce(false).mockReturnValueOnce(true);
                renderComponent([tokenAuthenticationMethodData]);
                const button = copyButton();

                button.click();
                fixture.detectChanges();
                expect(button.disabled).toBe(false);
                expect(button.textContent).toContain('artemisApp.exerciseActions.copyUrl');

                button.click();
                fixture.detectChanges();
                expect(button.disabled).toBe(true);
                expect(button.textContent).toContain('artemisApp.exerciseActions.copiedUrl');

                vi.advanceTimersByTime(2000);
                fixture.detectChanges();
                expect(button.disabled).toBe(false);
                expect(button.textContent).toContain('artemisApp.exerciseActions.copyUrl');
            } finally {
                vi.useRealTimers();
            }
        });
    });

    describe('authentication warnings', () => {
        it.each([
            {
                description: 'the selected SSH method has no configured key',
                authenticationMethodData: {
                    authenticationMethod: RepositoryAuthenticationMethod.SSH,
                    url: sshUrl,
                    sshKeyState: { status: 'missing' },
                } satisfies AuthenticationMethodData,
                expectedTranslationKey: 'artemisApp.exerciseActions.codePopoverSshKeyTip',
            },
            {
                description: 'the selected SSH method has an expired key',
                authenticationMethodData: {
                    authenticationMethod: RepositoryAuthenticationMethod.SSH,
                    url: sshUrl,
                    sshKeyState: { status: 'configured', hasExpiredKeys: true },
                } satisfies AuthenticationMethodData,
                expectedTranslationKey: 'artemisApp.exerciseActions.codePopoverSshKeyExpiredTip',
            },
        ])('should render the SSH warning when $description', ({ authenticationMethodData, expectedTranslationKey }) => {
            renderComponent([authenticationMethodData]);

            expect(translatedElement(expectedTranslationKey)).not.toBeNull();
        });

        it('should render no SSH warning when all configured keys are valid', () => {
            renderComponent([sshAuthenticationMethodData]);

            expect(translatedElement('artemisApp.exerciseActions.codePopoverSshKeyTip')).toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.codePopoverSshKeyExpiredTip')).toBeNull();
        });

        it.each([
            { description: 'token authentication', alternativeAuthenticationMethodData: tokenAuthenticationMethodData },
            { description: 'SSH authentication', alternativeAuthenticationMethodData: sshAuthenticationMethodData },
        ])('should render the password warning when $description is available', ({ alternativeAuthenticationMethodData }) => {
            renderComponent([passwordAuthenticationMethodData, alternativeAuthenticationMethodData]);
            expect(translatedElement('artemisApp.exerciseActions.passwordAuthenticationWarning')).not.toBeNull();
            const tooltipElement = fixture.debugElement.query(By.directive(TumAetUiTooltipDirective));
            const tooltip = tooltipElement.injector.get(TumAetUiTooltipDirective);
            expect(tooltip.content()).toBe('artemisApp.exerciseActions.passwordAuthenticationWarningTooltip');
            expect(tooltip.placement()).toBe('left');
        });

        it('should render no password warning when no alternative authentication method is available', () => {
            renderComponent([passwordAuthenticationMethodData]);
            expect(translatedElement('artemisApp.exerciseActions.passwordAuthenticationWarning')).toBeNull();
        });

        it.each([
            { description: 'Token', authenticationMethodData: tokenAuthenticationMethodData },
            { description: 'SSH', authenticationMethodData: sshAuthenticationMethodData },
        ])('should render no password warning when $description is selected', ({ authenticationMethodData }) => {
            renderComponent([authenticationMethodData, passwordAuthenticationMethodData]);
            expect(translatedElement('artemisApp.exerciseActions.passwordAuthenticationWarning')).toBeNull();
        });
    });

    describe('alternative actions', () => {
        it('should render no alternative-action control when no alternative is available', () => {
            renderComponent([tokenAuthenticationMethodData]);

            expect(translatedElement('artemisApp.exerciseActions.showAlternatives')).toBeNull();
            expect(fixture.nativeElement.querySelector('iframe[name="hidden-iframe"]')).toBeNull();
        });

        it.each([
            { description: 'sanitization rejects it', sanitizedUrl: null },
            { description: 'sanitization marks it unsafe', sanitizedUrl: 'unsafe:jetbrains://idea/checkout?url=repository' },
        ])('should not render an IDE action when $description', ({ sanitizedUrl }) => {
            externalCloningService.buildIdeUrl.mockReturnValue('jetbrains://idea/checkout?url=repository');
            sanitizer.sanitize.mockReturnValue(sanitizedUrl);

            renderComponent([tokenAuthenticationMethodData]);

            expect(translatedElement('artemisApp.exerciseActions.showAlternatives')).toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.ideButton.openIn')).toBeNull();
        });

        it('should reveal a trusted IDE action', () => {
            const ideUrl = 'jetbrains://idea/checkout?url=repository';
            externalCloningService.buildIdeUrl.mockReturnValue(ideUrl);
            trustUrl(ideUrl);
            renderComponent([tokenAuthenticationMethodData]);

            const showAlternativesButton = translatedElement('artemisApp.exerciseActions.showAlternatives') as HTMLButtonElement;
            showAlternativesButton.click();
            fixture.detectChanges();

            const ideAction = translatedElement('artemisApp.exerciseActions.ideButton.openIn')?.closest('a');
            expect(showAlternativesButton.isConnected).toBe(false);
            expect(ideAction?.getAttribute('href')).toBe(ideUrl);
            expect(ideAction?.getAttribute('target')).toBe('hidden-iframe');
        });

        it.each([
            { description: 'sanitization rejects it', sanitizedUrl: null },
            { description: 'sanitization marks it unsafe', sanitizedUrl: 'unsafe:sourcetree://cloneRepo?cloneUrl=repository' },
        ])('should not render a SourceTree action when $description', ({ sanitizedUrl }) => {
            externalCloningService.buildSourceTreeUrl.mockReturnValue('sourcetree://cloneRepo?cloneUrl=repository');
            sanitizer.sanitize.mockReturnValue(sanitizedUrl);

            renderComponent([passwordAuthenticationMethodData]);

            expect(translatedElement('artemisApp.exerciseActions.showAlternatives')).toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.openInSourceTree.button')).toBeNull();
        });

        it('should reveal a trusted SourceTree action', () => {
            const sourceTreeUrl = 'sourcetree://cloneRepo?cloneUrl=repository';
            externalCloningService.buildSourceTreeUrl.mockReturnValue(sourceTreeUrl);
            trustUrl(sourceTreeUrl);
            renderComponent([passwordAuthenticationMethodData]);

            (translatedElement('artemisApp.exerciseActions.showAlternatives') as HTMLButtonElement).click();
            fixture.detectChanges();

            const sourceTreeAction = translatedElement('artemisApp.exerciseActions.openInSourceTree.button')?.closest('a');
            expect(sourceTreeAction?.getAttribute('href')).toBe(sourceTreeUrl);
        });

        it('should show the online IDE action and launch Theia', () => {
            theiaService.startOnlineIDE.mockResolvedValue();
            renderComponent([tokenAuthenticationMethodData], false, theiaLinkData);

            (translatedElement('artemisApp.exerciseActions.showAlternatives') as HTMLButtonElement).click();
            fixture.detectChanges();

            const onlineIdeAction = translatedElement('artemisApp.exerciseActions.openOnlineIDE')?.closest('a') as HTMLAnchorElement;
            expect(onlineIdeAction).not.toBeNull();
            onlineIdeAction.click();
            expect(theiaService.startOnlineIDE).toHaveBeenCalledWith(
                theiaLinkData.theiaPortalURL,
                theiaLinkData.theiaImage,
                theiaLinkData.repositoryUrl,
                theiaLinkData.userName,
                theiaLinkData.userEmail,
            );
        });

        it('should launch Theia with its token URL when SSH is selected', () => {
            localStorageService.retrieve.mockReturnValue(RepositoryAuthenticationMethod.SSH);
            theiaService.startOnlineIDE.mockResolvedValue();
            renderComponent([tokenAuthenticationMethodData, sshAuthenticationMethodData], false, theiaLinkData);
            expectSelectedAuthenticationMethod('SSH', sshUrl);

            (translatedElement('artemisApp.exerciseActions.showAlternatives') as HTMLButtonElement).click();
            fixture.detectChanges();
            (translatedElement('artemisApp.exerciseActions.openOnlineIDE')?.closest('a') as HTMLAnchorElement).click();

            expect(theiaService.startOnlineIDE).toHaveBeenCalledWith(
                theiaLinkData.theiaPortalURL,
                theiaLinkData.theiaImage,
                tokenUrl,
                theiaLinkData.userName,
                theiaLinkData.userEmail,
            );
        });

        it('should render every available expanded alternative', () => {
            const ideUrl = 'jetbrains://idea/checkout?url=repository';
            const sourceTreeUrl = 'sourcetree://cloneRepo?cloneUrl=repository';
            externalCloningService.buildIdeUrl.mockReturnValue(ideUrl);
            externalCloningService.buildSourceTreeUrl.mockReturnValue(sourceTreeUrl);
            sanitizer.sanitize.mockImplementation((_context, url) => (typeof url === 'string' ? url : null));
            sanitizer.bypassSecurityTrustUrl.mockImplementation((url) => ({ toString: () => url }) as SafeUrl);

            renderComponent([tokenAuthenticationMethodData], true, theiaLinkData);

            expect(translatedElement('artemisApp.exerciseActions.showAlternatives')).toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.ideButton.openIn')).not.toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.openOnlineIDE')).not.toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.openInSourceTree.button')).not.toBeNull();
            expect(translatedElement('artemisApp.exerciseActions.openInSourceTree.button')?.closest('a')?.getAttribute('target')).toBe('hidden-iframe');
        });
    });
});
