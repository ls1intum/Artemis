import { Injectable } from '@angular/core';
import { faMoon, faSun, IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Observable } from 'rxjs';

export const THEME_LOCAL_STORAGE_KEY = 'artmisApp.theme.current';
export const THEME_OVERRIDE_ID = 'artemis-theme-override';

export class Theme {
    public static readonly LIGHT = new Theme('LIGHT', true, undefined, 'artemisApp.themes.light', faSun, 'chrome', 'dreamweaver');
    public static readonly DARK = new Theme('DARK', false, 'theme-dark.css', 'artemisApp.themes.dark', faMoon, 'monokai', 'monokai');

    private constructor(
        identifier: string,
        isDefault: boolean,
        fileName: string | undefined,
        displayTranslationKey: string,
        icon: IconDefinition,
        markdownAceTheme: string,
        codeAceTheme: string,
    ) {
        this.identifier = identifier;
        this.isDefault = isDefault;
        this.fileName = fileName;
        this.displayTranslationKey = displayTranslationKey;
        this.icon = icon;
        this.markdownAceTheme = markdownAceTheme;
        this.codeAceTheme = codeAceTheme;
    }

    public readonly identifier: string;
    public readonly isDefault: boolean;
    public readonly fileName: string | undefined;
    public readonly displayTranslationKey: string;
    public readonly icon: IconDefinition;
    public readonly markdownAceTheme: string;
    public readonly codeAceTheme: string;

    public static get all(): Theme[] {
        return [this.LIGHT, this.DARK];
    }
}

@Injectable({
    providedIn: 'root',
})
export class ThemeService {
    private currentTheme: Theme = Theme.LIGHT;
    private currentThemeSubject: BehaviorSubject<Theme> = new BehaviorSubject<Theme>(Theme.LIGHT);

    isAutoDetected = true;

    constructor(private localStorageService: LocalStorageService) {}

    public getCurrentTheme(): Theme {
        return this.currentTheme;
    }

    public getCurrentThemeObservable(): Observable<Theme> {
        return this.currentThemeSubject.asObservable();
    }

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

    public applyTheme(theme: Theme) {
        this.applyThemeInternal(theme, false);
    }

    private applyThemeInternal(theme: Theme, byAutoDetection: boolean) {
        if (!theme) {
            return;
        }

        if (this.currentTheme === theme) {
            // The theme has been explicitly set. Store it even if it's already applied
            this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, theme.identifier);
            this.isAutoDetected = byAutoDetection;
            return;
        }

        const overrideTag = document.getElementById(THEME_OVERRIDE_ID);

        if (theme.isDefault) {
            overrideTag?.remove();

            this.isAutoDetected = byAutoDetection;
            this.currentTheme = theme;
            this.currentThemeSubject.next(theme);
        } else {
            const head = document.getElementsByTagName('head')[0];

            const newTag = document.createElement('link');
            newTag.id = THEME_OVERRIDE_ID;
            newTag.rel = 'stylesheet';
            newTag.href = theme.fileName!;

            newTag.onload = () => {
                overrideTag?.remove();
                this.isAutoDetected = byAutoDetection;
                this.currentTheme = theme;
                this.currentThemeSubject.next(theme);
            };

            const existingLinkTags = head.getElementsByTagName('link');
            const lastLinkTag = existingLinkTags[existingLinkTags.length - 1];
            head.insertBefore(newTag, lastLinkTag?.nextSibling);
        }

        this.localStorageService.store(THEME_LOCAL_STORAGE_KEY, theme.identifier);
    }
}
