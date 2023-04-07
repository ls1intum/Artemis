import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';

describe('PrivacyComponent', () => {
    let component: PrivacyComponent;
    let fixture: ComponentFixture<PrivacyComponent>;
    let privacyStatementService: PrivacyStatementService;
    let localConversionService: LocaleConversionService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PrivacyComponent, MockDirective(TranslateDirective), MockPipe(HtmlForMarkdownPipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(PrivacyComponent);
        component = fixture.componentInstance;
        privacyStatementService = TestBed.inject(PrivacyStatementService);
        localConversionService = TestBed.inject(LocaleConversionService);
        fixture.detectChanges();
    });

    it('should load privacy statement on init in correct language', () => {
        const privacyServiceSpy = jest.spyOn(privacyStatementService, 'getPrivacyStatement');
        localConversionService.locale = 'en';
        component.ngOnInit();
        fixture.detectChanges();
        expect(privacyServiceSpy).toHaveBeenCalledOnce();
        expect(privacyServiceSpy).toHaveBeenCalledWith(PrivacyStatementLanguage.ENGLISH);
    });
});
