import { signal } from '@angular/core';
import { Theme } from 'app/core/theme/theme.service';

export class MockThemeService {
    private _currentTheme = signal(Theme.LIGHT);
    public readonly currentTheme = this._currentTheme.asReadonly();

    private _preference = signal<Theme | undefined>(undefined);
    public readonly preference = this._preference.asReadonly();

    public applyTheme(theme: Theme | undefined) {
        this._preference.set(theme);
        this._currentTheme.set(theme ?? Theme.LIGHT);
    }

    public print() {}
}
