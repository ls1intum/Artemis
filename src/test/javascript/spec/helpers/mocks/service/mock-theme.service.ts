import { Theme } from 'app/core/theme/theme.service';
import { BehaviorSubject, Observable } from 'rxjs';

export class MockThemeService {
    private currentTheme: Theme = Theme.LIGHT;
    private currentThemeSubject: BehaviorSubject<Theme> = new BehaviorSubject<Theme>(Theme.LIGHT);

    public isByAutoDetection = false;

    public getCurrentTheme(): Theme {
        return this.currentTheme;
    }

    public getCurrentThemeObservable(): Observable<Theme> {
        return this.currentThemeSubject.asObservable();
    }

    public restoreTheme() {}

    public applyTheme(theme: Theme) {
        this.applyThemeInternal(theme, false);
    }

    public applyThemeInternal(theme: Theme, isByAutoDetection: boolean) {
        this.currentTheme = theme;
        this.currentThemeSubject.next(theme);
    }
}
