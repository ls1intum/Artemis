import { Injectable } from '@angular/core';
import { IconDefinition, faMoon, faSun } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Observable } from 'rxjs';

export const THEME_LOCAL_STORAGE_KEY = 'artemisapp.theme.preference';
export const THEME_OVERRIDE_ID = 'artemis-theme-override';

/**
 * Contains definitions for a theme.
 * If you add new themes, make sure to adapt the theme switch component which currently only supports two themes.
 */
export class Theme {
    public static readonly LIGHT = new Theme('LIGHT', true, undefined, faSun, 'chrome', 'dreamweaver');
    public static readonly DARK = new Theme('DARK', false, 'theme-dark.css', faMoon, 'dracula', 'dracula');

    private constructor(identifier: string, isDefault: boolean, fileName: string | undefined, icon: IconDefinition, markdownAceTheme: string, codeAceTheme: string) {
        this.identifier = identifier;
        this.isDefault = isDefault;
        this.fileName = fileName;
        this.icon = icon;
        this.markdownAceTheme = markdownAceTheme;
        this.codeAceTheme = codeAceTheme;
    }

    public readonly identifier: string;
    public readonly isDefault: boolean;
    public readonly fileName: string | undefined;
    public readonly icon: IconDefinition;
    public readonly markdownAceTheme: string;
    public readonly codeAceTheme: string;

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
     * The currently applied theme
     * @private
     */
    private currentTheme: Theme = Theme.LIGHT;
    /**
     * A behavior subject that fires for each new applied theme.
     * @private
     */
    private currentThemeSubject: BehaviorSubject<Theme> = new BehaviorSubject<Theme>(Theme.LIGHT);
    /**
     * A behavior subject that fires if the user preference changes.
     * Can be either a theme for an explicit theme or undefined if system settings are preferred
     * @private
     */
    private preferenceSubject: BehaviorSubject<Theme | undefined> = new BehaviorSubject<Theme | undefined>(undefined);

    private darkSchemeMediaQuery: MediaQueryList;

    constructor(private localStorageService: LocalStorageService) {}

    /**
     * Returns the currently active theme.
     */
    public getCurrentTheme(): Theme {
        return this.currentTheme;
    }

    /**
     * Returns an observable that will be fired immediately for the current theme and for each future theme change until unsubscribed.
     */
    public getCurrentThemeObservable(): Observable<Theme> {
        return this.currentThemeSubject.asObservable();
    }

    /**
     * Returns an observable that will be fired immediately for the current user preference and if the user preference changes.
     * Can be either a theme for an explicit theme or undefined if system settings are preferred
     */
    public getPreferenceObservable(): Observable<Theme | undefined> {
        return this.preferenceSubject.asObservable();
    }

    /**
     * Should be called once on application startup.
     * Sets up the system preference listener and applies the theme initially
     * Sets up a local storage listener to account for changes in other tabs
     */
    initialize() {
        this.darkSchemeMediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        if (this.darkSchemeMediaQuery.media !== 'not all') {
            this.darkSchemeMediaQuery.addEventListener('change', () => this.applyPreferredTheme());
        }

        addEventListener('storage', (event) => {
            if (event.key === 'jhi-' + THEME_LOCAL_STORAGE_KEY) {
                this.preferenceSubject.next(this.getStoredTheme());
                this.applyPreferredTheme();
            }
        });

        this.applyPreferredTheme();
        this.preferenceSubject.next(this.getStoredTheme());
    }

    /**
     * Applies the preferred theme.
     * The preferred theme is either
     * - the theme stored in local storage, if present, or else
     * - the system preference, if present, or else
     * - the default theme
     * @private
     */
    private applyPreferredTheme() {
        const storedTheme = this.getStoredTheme();
        if (storedTheme) {
            this.applyThemeInternal(storedTheme);
            return;
        }

        if (this.darkSchemeMediaQuery.matches) {
            this.applyThemeInternal(Theme.DARK);
            return;
        }

        this.applyThemeInternal(Theme.LIGHT);
    }

    /**
     * Returns the theme preference stored in local storage or undefined if no preference is stored
     * @private
     */
    private getStoredTheme(): Theme | undefined {
        const storedIdentifier = this.localStorageService.retrieve(THEME_LOCAL_STORAGE_KEY);
        const storedTheme = Theme.all.find((theme) => theme.identifier === storedIdentifier);

        // An unknown theme was stored. Let's clear it
        if (storedIdentifier && !storedTheme) {
            this.storePreference(undefined);
        }

        return storedTheme;
    }

    /**
     * Prints the current page.
     * Disables any theme override before doing that to ensure that we print in default theme.
     * Resets the theme afterwards if needed
     */
    public print() {
        const overrideTag: any = document.getElementById(THEME_OVERRIDE_ID);
        if (overrideTag) {
            overrideTag.rel = 'none-tmp';
        }
        setTimeout(() => window.print(), 250);
        setTimeout(() => {
            if (overrideTag) {
                overrideTag.rel = 'stylesheet';
            }
        }, 500);
    }

    /**
     * Applies the specified theme.
     * Should only be called upon user request.
     * Stores the preference in local storage.
     *
     * @param theme the theme to be applied; pass undefined to use system preference mode
     */
    public applyThemeExplicitly(theme: Theme | undefined) {
        this.storePreference(theme);
        this.applyPreferredTheme();
    }

    private applyThemeInternal(theme: Theme) {
        if (!theme) {
            return;
        }

        // Do not inject or remove anything from the DOM if the applied theme is the current theme
        if (this.currentTheme === theme) {
            return;
        }

        // Get current <link> theme override
        const overrideTag = document.getElementById(THEME_OVERRIDE_ID);

        if (theme.isDefault) {
            // The default theme is always injected by Angular; therefore, we just need to remove
            // our theme override, if present
            overrideTag?.remove();

            this.currentTheme = theme;
            this.currentThemeSubject.next(theme);
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
            // and fire the subject to inform other services and components
            newTag.onload = () => {
                overrideTag?.remove();
                this.currentTheme = theme;
                this.currentThemeSubject.next(theme);
            };

            // Insert the new stylesheet link tag after the last existing link tag
            const existingLinkTags = head.getElementsByTagName('link');
            const lastLinkTag = existingLinkTags[existingLinkTags.length - 1];
            head.insertBefore(newTag, lastLinkTag?.nextSibling);
        }
    }

    private storePreference(theme?: Theme) {
        if (theme) {
            this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, theme.identifier);
        } else {
            this.localStorageService.clear(THEME_LOCAL_STORAGE_KEY);
        }

        if (this.preferenceSubject.getValue() !== theme) {
            this.preferenceSubject.next(theme);
        }
    }
}
