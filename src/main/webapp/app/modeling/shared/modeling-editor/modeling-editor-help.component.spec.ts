import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { ModelingEditorHelpComponent } from 'app/modeling/shared/modeling-editor/modeling-editor-help.component';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ModelingEditorHelpComponent', () => {
    let component: ModelingEditorHelpComponent;
    let themeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ModelingEditorHelpComponent],
            providers: [
                { provide: ThemeService, useClass: MockThemeService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        component = TestBed.createComponent(ModelingEditorHelpComponent).componentInstance;
        themeService = TestBed.inject(ThemeService);
    });

    // That every walkthrough topic has a shipped image pair is asserted end to end, by the help dialog
    // checks in `ModelingEditorFullscreen.spec.ts`. Only the theme swap is worth a unit test.
    it('uses walkthrough images for the active Artemis theme', () => {
        expect(component['walkthroughs']()).not.toHaveLength(0);
        expect(component['walkthroughs']().every(({ image }) => image.endsWith('-light.png'))).toBe(true);

        themeService.applyThemePreference(Theme.DARK);

        expect(component['walkthroughs']().every(({ image }) => image.endsWith('-dark.png'))).toBe(true);
    });
});
