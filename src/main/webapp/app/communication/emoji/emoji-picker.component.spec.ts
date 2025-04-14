import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { PickerComponent } from '@ctrl/ngx-emoji-mart';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { TranslateService } from '@ngx-translate/core';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { EmojiPickerComponent } from 'app/communication/emoji/emoji-picker.component';

describe('EmojiPickerComponent', () => {
    let fixture: ComponentFixture<EmojiPickerComponent>;
    let comp: EmojiPickerComponent;
    let mockThemeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslatePipeMock, EmojiPickerComponent, MockComponent(PickerComponent)],
            providers: [
                { provide: ThemeService, useClass: MockThemeService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                mockThemeService = TestBed.inject(ThemeService);
                fixture = TestBed.createComponent(EmojiPickerComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should react to theme changes', () => {
        expect(comp.dark()).toBeFalse();
        expect(comp.singleImageFunction()({ unified: '1F519' } as EmojiData)).toBe('');

        mockThemeService.applyThemePreference(Theme.DARK);

        expect(comp.dark()).toBeTrue();
        expect(comp.singleImageFunction()({ unified: '1F519' } as EmojiData)).toBe('public/emoji/1f519.png');
    });

    it('should emit an event on emoji select', () => {
        const emitSpy = jest.spyOn(comp.emojiSelect, 'emit').mockReturnValue();
        comp.onEmojiSelect({ test: 123 });
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith({ test: 123 });
    });
});
