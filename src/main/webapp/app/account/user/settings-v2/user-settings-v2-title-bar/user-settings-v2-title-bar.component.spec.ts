import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UserSettingsV2TitleBarComponent } from './user-settings-v2-title-bar.component';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

describe('UserSettingsV2TitleBarComponent', () => {
    let fixture: ComponentFixture<UserSettingsV2TitleBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsV2TitleBarComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(UserSettingsV2TitleBarComponent);
    });

    it('renders the active route title', () => {
        fixture.componentRef.setInput('pageTitle', 'artemisApp.userSettings.navigation.credentials');
        fixture.detectChanges();

        const heading = fixture.debugElement.query(By.css('h5'));
        const translateDirective = heading.injector.get(TranslateDirective);
        expect(translateDirective.jhiTranslate()).toBe('artemisApp.userSettings.navigation.credentials');
    });
});
