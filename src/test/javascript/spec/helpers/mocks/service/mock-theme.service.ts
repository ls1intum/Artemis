import { Theme } from 'app/core/theme/theme.service';
import { BehaviorSubject, Observable } from 'rxjs';

export class MockThemeService {
    private currentTheme: Theme = Theme.LIGHT;
    private currentThemeSubject: BehaviorSubject<Theme> = new BehaviorSubject<Theme>(Theme.LIGHT);
    private preferenceSubject: BehaviorSubject<Theme | undefined> = new BehaviorSubject<Theme | undefined>(undefined);

    public isByAutoDetection = false;

    public getCurrentTheme(): Theme {
        return this.currentTheme;
    }

    public getCurrentThemeObservable(): Observable<Theme> {
        return this.currentThemeSubject.asObservable();
    }

    public getPreferenceObservable(): Observable<Theme | undefined> {
        return this.preferenceSubject.asObservable();
    }

    public restoreTheme() {}

    public applyThemeExplicitly(theme: Theme) {
        this.currentTheme = theme;
        this.currentThemeSubject.next(theme);
        this.preferenceSubject.next(theme);
    }

    public print() {}
}
