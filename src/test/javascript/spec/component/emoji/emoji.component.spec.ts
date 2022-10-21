import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';

describe('EmojiComponent', () => {
    let fixture: ComponentFixture<EmojiComponent>;
    let comp: EmojiComponent;
    let mockThemeService: ThemeService;
    let themeSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                mockThemeService = TestBed.inject(ThemeService);
                themeSpy = jest.spyOn(mockThemeService, 'getCurrentThemeObservable').mockReturnValue(of(Theme.DARK));
                fixture = TestBed.createComponent(EmojiComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should subscribe and unsubscribe to the theme service and set dark flag', () => {
        expect(themeSpy).toHaveBeenCalledOnce();
        expect(comp.dark).toBeTrue();
        expect(comp.themeSubscription).toBeDefined();

        const subSpy = jest.spyOn(comp.themeSubscription, 'unsubscribe');

        comp.ngOnDestroy();
        expect(subSpy).toHaveBeenCalledOnce();
    });
});
