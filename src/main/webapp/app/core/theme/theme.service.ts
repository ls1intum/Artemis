import { Injectable } from '@angular/core';
import { faMoon, faSun, IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Observable } from 'rxjs';

export const THEME_LOCAL_STORAGE_KEY = 'artmisApp.theme.current';
export const THEME_OVERRIDE_ID = 'artemis-theme-override';

/**
 * Contains definitions for a theme.
 * If you add new themes, make sure to adapt the theme switch component which currently only supports two themes.
 */
export class Theme {
    public static readonly LIGHT = new Theme('LIGHT', true, undefined, faSun, 'chrome', 'dreamweaver');
    public static readonly DARK = new Theme('DARK', false, 'theme-dark.css', faMoon, 'monokai', 'monokai');

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
     * Indicates whether the current theme selection resulted from an automated detection based on the environment.
     * Will only be the case after application start up if there's no stored theme preference and we fall back to OS preferences.
     */
    isAutoDetected = true;

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
     * Should be called once on application startup to either
     * - load the theme from the theme preference stored in local storage, or
     * - load the theme based on the OS preferences for dark user interfaces using a CSS media query
     */
    restoreTheme() {
        const storedIdentifier = this.localStorageService.retrieve(THEME_LOCAL_STORAGE_KEY);
        if (storedIdentifier) {
            const storedTheme = Theme.all.find((theme) => theme.identifier === storedIdentifier);

            if (storedTheme) {
                this.applyTheme(storedTheme);
                return;
            } else {
                console.warn('Unknown theme found in local storage: ' + storedIdentifier);
            }
        }

        // Did not find a stored theme!
        // Let's check if the user prefers dark mode on the OS level / globally
        if (window.matchMedia('(prefers-color-scheme)').media !== 'not all' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            this.applyThemeInternal(Theme.DARK, true);
            return;
        }

        // The default LIGHT theme is always applied automatically; no need for fallback handling here.
        // Anyways, be "detected" that the user prefers the light mode, so lets set the flag
        this.isAutoDetected = true;
    }

    /**
     * Applies the specified theme.
     * If the theme is the current theme, nothing will be changed, but the preference will be stored in local storage.
     * @param theme the theme to be applied
     */
    public applyTheme(theme: Theme) {
        this.applyThemeInternal(theme, false);
    }

    private applyThemeInternal(theme: Theme, byAutoDetection: boolean) {
        if (!theme) {
            return;
        }

        // Do not inject or remove anything from the DOM if the applied theme is the current theme
        if (this.currentTheme === theme) {
            // The theme has been explicitly set. Store it even if it's already applied
            this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, theme.identifier);
            this.isAutoDetected = byAutoDetection;
            return;
        }

        // Get current <link> theme override
        const overrideTag = document.getElementById(THEME_OVERRIDE_ID);

        if (theme.isDefault) {
            // The default theme is always injected by Angular; therefore, we just need to remove
            // our theme override, if present
            overrideTag?.remove();

            this.isAutoDetected = byAutoDetection;
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
            newTag.href = theme.fileName!;

            // As soon as the new style sheet loaded, remove the old override (if present)
            // and fire the subject to inform other services and components
            newTag.onload = () => {
                overrideTag?.remove();
                this.isAutoDetected = byAutoDetection;
                this.currentTheme = theme;
                this.currentThemeSubject.next(theme);
            };

            // Insert the new stylesheet link tag after the last existing link tag
            const existingLinkTags = head.getElementsByTagName('link');
            const lastLinkTag = existingLinkTags[existingLinkTags.length - 1];
            head.insertBefore(newTag, lastLinkTag?.nextSibling);
        }

        // Finally, store the selected preference in local storage
        this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, theme.identifier);
    }
}
