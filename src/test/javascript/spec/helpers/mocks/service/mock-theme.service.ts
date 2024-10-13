import { signal } from '@angular/core';
import { Theme } from 'app/core/theme/theme.service';

export class MockThemeService {
    private _currentTheme = signal(Theme.LIGHT);
    public readonly currentTheme = this._currentTheme.asReadonly();

    private _userPreference = signal<Theme | undefined>(undefined);
    public readonly userPreference = this._userPreference.asReadonly();

    public applyThemePreference(preference: Theme | undefined) {
        this._userPreference.set(preference);
        this._currentTheme.set(preference ?? Theme.LIGHT);
    }

    public print() {}
}
