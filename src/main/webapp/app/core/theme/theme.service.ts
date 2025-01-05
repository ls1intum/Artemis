import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { IconDefinition, faMoon, faSun } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';

export const THEME_LOCAL_STORAGE_KEY = 'artemisapp.theme.preference';
export const THEME_OVERRIDE_ID = 'artemis-theme-override';

/**
 * Contains definitions for a theme.
 * If you add new themes, make sure to adapt the theme switch component which currently only supports two themes.
 */
export class Theme {
    public static readonly LIGHT = new Theme('LIGHT', true, undefined, faSun);
    public static readonly DARK = new Theme('DARK', false, 'theme-dark.css', faMoon);

    private constructor(identifier: string, isDefault: boolean, fileName: string | undefined, icon: IconDefinition) {
        this.identifier = identifier;
        this.isDefault = isDefault;
        this.fileName = fileName;
        this.icon = icon;
    }

    public readonly identifier: string;
    public readonly isDefault: boolean;
    public readonly fileName: string | undefined;
    public readonly icon: IconDefinition;

    /**
     * Returns an array with all available themes.
     */
    public static get all(): Theme[] {
        return [this.LIGHT, this.DARK];
    }
}

/**
 * Service that manages application UI theming.
 * Provides the current theme information to other components and services.
 * Applies new themes as requested from other components / services, usually the theme switcher component.
 */
@Injectable({
    providedIn: 'root',
})
export class ThemeService {
    /**
     * The user preference changes as WritableSignal.
     * If changed, the theme is applied immediately.
     */
    private _userPreference = signal<Theme | undefined>(undefined);

    /**
     * The user preference changes as ReadonlySignal.
     * Can be either a theme for an explicit theme or undefined if system settings are preferred.
     */
    public readonly userPreference = this._userPreference.asReadonly();

    /**
     * The system preference as WritableSignal.
     */
    private systemPreference = signal<Theme>(Theme.LIGHT);

    /**
     * The currently applied theme as Signal.
     */
    public currentTheme = computed(() => this.userPreference() ?? this.systemPreference());

    private localStorageService = inject(LocalStorageService);

    private darkSchemeMediaQuery: MediaQueryList;

    constructor() {
        effect(() => {
            // Apply the theme as soon as the currentTheme changes
            const currentTheme = this.currentTheme();
            untracked(() => this.applyTheme(currentTheme));
        });
    }

    /**
     * Should be called once on application startup.
     * Sets up the system preference listener and applies the theme initially
     * Sets up a local storage listener to account for changes in other tabs
     */
    initialize() {
        this.darkSchemeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        if (this.darkSchemeMediaQuery.media !== 'not all') {
            this.darkSchemeMediaQuery.addEventListener('change', () => {
                if (this.darkSchemeMediaQuery.matches) {
                    this.systemPreference.set(Theme.DARK);
                } else {
                    this.systemPreference.set(Theme.LIGHT);
                }
            });
        }

        addEventListener('storage', (event) => {
            if (event.key === 'jhi-' + THEME_LOCAL_STORAGE_KEY) {
                this._userPreference.set(this.getStoredThemePreference());
            }
        });

        this.systemPreference.set(this.darkSchemeMediaQuery.matches ? Theme.DARK : Theme.LIGHT);
        this._userPreference.set(this.getStoredThemePreference());
    }

    /**
     * Returns the theme preference stored in local storage or undefined if no preference is stored
     */
    private getStoredThemePreference(): Theme | undefined {
        const storedIdentifier = this.localStorageService.retrieve(THEME_LOCAL_STORAGE_KEY);
        const storedTheme = Theme.all.find((theme) => theme.identifier === storedIdentifier);

        // An unknown theme was stored. Let's clear it
        if (storedIdentifier && !storedTheme) {
            this.localStorageService.clear(THEME_LOCAL_STORAGE_KEY);
        }

        return storedTheme;
    }

    /**
     * Prints the current page.
     * Disables any theme override before doing that to ensure that we print in default theme.
     * Resets the theme afterward if needed
     */
    public async print(): Promise<void> {
        return new Promise<void>((resolve) => {
            const overrideTag: any = document.getElementById(THEME_OVERRIDE_ID);
            if (overrideTag) {
                overrideTag.rel = 'none-tmp';
            }
            setTimeout(() => {
                const notificationSidebarDisplayAttribute = this.hideNotificationSidebar();

                window.print();

                this.showNotificationSidebar(notificationSidebarDisplayAttribute);
            }, 250);
            setTimeout(() => {
                if (overrideTag) {
                    overrideTag.rel = 'stylesheet';
                }
                resolve();
            }, 500);
        });
    }

    /**
     * Applies the specified theme preference.
     * Should only be called upon user request.
     * Stores the preference in local storage.
     *
     * @param preference the theme to be applied; pass undefined to use system preference mode
     */
    public applyThemePreference(preference: Theme | undefined) {
        if (preference) {
            this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, preference.identifier);
        } else {
            this.localStorageService.clear(THEME_LOCAL_STORAGE_KEY);
        }
        this._userPreference.set(preference);
    }

    /**
     * Applies the theme to the application.
     *
     * Only call if the theme changed.
     *
     * @param theme the theme to apply
     */
    private applyTheme(theme: Theme) {
        // Get current <link> theme override
        const overrideTag = document.getElementById(THEME_OVERRIDE_ID);

        if (theme.isDefault) {
            // The default theme is always injected by Angular; therefore, we just need to remove
            // our theme override, if present
            overrideTag?.remove();
        } else {
            // If the theme is not the default theme, we need to add a theme override stylesheet to the page header

            // Select the head element
            const head = document.getElementsByTagName('head')[0];

            // Create new override tag from the current theme
            const newTag = document.createElement('link');
            newTag.id = THEME_OVERRIDE_ID;
            newTag.rel = 'stylesheet';
            // Use cache busting so the browser will reload the stylesheet at least once per hour
            newTag.href = theme.fileName! + '?_=' + new Date().setMinutes(0, 0, 0);

            // As soon as the new style sheet loaded, remove the old override (if present)
            newTag.onload = () => {
                overrideTag?.remove();
            };

            // Insert the new stylesheet link tag after the last existing link tag
            const existingLinkTags = head.getElementsByTagName('link');
            const lastLinkTag = existingLinkTags[existingLinkTags.length - 1];
            head.insertBefore(newTag, lastLinkTag?.nextSibling);
        }
    }

    /**
     * Hides the notification sidebar as there will be an overlay ove the whole page
     * that covers details of the exam summary (=> exam summary cannot be read).
     *
     * @return displayAttribute of the notification sidebar before hiding it
     */
    private hideNotificationSidebar(): string {
        return this.modifyNotificationSidebarDisplayStyling();
    }

    /**
     * After printing the notification sidebar shall be displayed again.
     *
     * @param displayAttributeBeforeHide to reset the notification sidebar to its previous state
     * @return displayAttribute of the notification sidebar before hiding it
     */
    private showNotificationSidebar(displayAttributeBeforeHide: string): string {
        return this.modifyNotificationSidebarDisplayStyling(displayAttributeBeforeHide);
    }

    /**
     * @param newDisplayAttribute that is set for the {@link NotificationSidebarComponent}
     * @return displayAttribute of the notification sidebar before hiding it
     */
    private modifyNotificationSidebarDisplayStyling(newDisplayAttribute?: string): string {
        const notificationSidebarElement: any = document.getElementById('notification-sidebar');
        let displayBefore = '';

        if (notificationSidebarElement) {
            displayBefore = notificationSidebarElement.style.display;
            notificationSidebarElement.style.display = newDisplayAttribute !== undefined ? newDisplayAttribute : 'none';
        }
        return displayBefore;
    }
}
