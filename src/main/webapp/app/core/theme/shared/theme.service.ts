import { Service, computed, effect, inject, signal, untracked } from '@angular/core';
import { IconDefinition, faMoon, faSun } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

export const THEME_LOCAL_STORAGE_KEY = 'artemisapp.theme.preference';
const THEME_ATTRIBUTE = 'data-theme';
const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)';

/**
 * Contains definitions for a theme.
 * If you add new themes, make sure to adapt the theme switch component which currently only supports two themes.
 */
export class Theme {
    public static readonly LIGHT = new Theme('LIGHT', 'light', faSun);
    public static readonly DARK = new Theme('DARK', 'dark', faMoon);

    private constructor(identifier: string, attributeValue: 'light' | 'dark', icon: IconDefinition) {
        this.identifier = identifier;
        this.attributeValue = attributeValue;
        this.icon = icon;
    }

    public readonly identifier: string;
    /** The value of the `data-theme` attribute of the root element that selects the tokens of this theme. */
    public readonly attributeValue: 'light' | 'dark';
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
@Service()
export class ThemeService {
    private localStorageService = inject(LocalStorageService);

    /**
     * The user preference changes as WritableSignal.
     * If changed, the theme is applied immediately.
     */
    private _userPreference = signal<Theme | undefined>(this.getStoredThemePreference());

    /**
     * The user preference changes as ReadonlySignal.
     * Can be either a theme for an explicit theme or undefined if system settings are preferred.
     */
    public readonly userPreference = this._userPreference.asReadonly();

    /**
     * The system preference as WritableSignal.
     * Like the user preference, it has its real value from the start: the script in `index.html` has already set the
     * theme of the page from the same two sources, and the first run of the effect below must not change it back.
     */
    private systemPreference = signal<Theme>(typeof window.matchMedia === 'function' && window.matchMedia(DARK_SCHEME_QUERY).matches ? Theme.DARK : Theme.LIGHT);

    /**
     * The currently applied theme as Signal.
     */
    public currentTheme = computed(() => this.userPreference() ?? this.systemPreference());

    private darkSchemeMediaQuery!: MediaQueryList; // set in initialize(), called once on application startup before any read

    constructor() {
        effect(() => {
            // Apply the theme as soon as the currentTheme changes
            const currentTheme = this.currentTheme();
            untracked(() => this.applyTheme(currentTheme));
        });
    }

    /**
     * Should be called once on application startup.
     * Sets up the system preference listener
     * Sets up a local storage listener to account for changes in other tabs
     */
    initialize() {
        this.darkSchemeMediaQuery = window.matchMedia(DARK_SCHEME_QUERY);
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
            if (event.key === THEME_LOCAL_STORAGE_KEY) {
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
        const storedIdentifier = this.localStorageService.retrieve<string>(THEME_LOCAL_STORAGE_KEY);
        const storedTheme = Theme.all.find((theme) => theme.identifier === storedIdentifier);

        // An unknown theme was stored. Let's clear it
        if (storedIdentifier && !storedTheme) {
            this.localStorageService.remove(THEME_LOCAL_STORAGE_KEY);
        }

        return storedTheme;
    }

    /**
     * Prints the current page.
     * Switches to the light theme before doing that to ensure that we print in the default theme.
     * Resets the theme afterward if needed
     */
    public async print(): Promise<void> {
        return new Promise<void>((resolve) => {
            this.applyTheme(Theme.LIGHT);
            setTimeout(() => {
                const notificationSidebarDisplayAttribute = this.hideNotificationSidebar();

                window.print();

                this.showNotificationSidebar(notificationSidebarDisplayAttribute);
            }, 250);
            setTimeout(() => {
                // the theme may have changed while the print dialog was open
                this.applyTheme(this.currentTheme());
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
            this.localStorageService.remove(THEME_LOCAL_STORAGE_KEY);
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
        // Both themes are part of the one global stylesheet; the attribute selects the tokens of the dark theme.
        // Embedded widgets that follow the `data-theme` convention switch with Artemis: the Apollon modeling
        // editor reads the attribute from any ancestor for its light/dark base.
        document.documentElement.setAttribute(THEME_ATTRIBUTE, theme.attributeValue);
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
     * @param newDisplayAttribute to reset the notification sidebar to its previous state
     * @return displayAttribute of the notification sidebar before hiding it
     */
    private modifyNotificationSidebarDisplayStyling(newDisplayAttribute?: string): string {
        const notificationSidebarElement: HTMLElement | null = document.getElementById('notification-sidebar');
        let displayBefore = '';

        if (notificationSidebarElement) {
            displayBefore = notificationSidebarElement.style.display;
            notificationSidebarElement.style.display = newDisplayAttribute !== undefined ? newDisplayAttribute : 'none';
        }
        return displayBefore;
    }
}
