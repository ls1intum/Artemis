import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';

describe('PrivacyComponent', () => {
    let component: PrivacyComponent;
    let fixture: ComponentFixture<PrivacyComponent>;
    let privacyStatementService: PrivacyStatementService;
    let languageHelper: JhiLanguageHelper;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PrivacyComponent, MockDirective(TranslateDirective), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                {
                    provide: SessionStorageService,
                    use: MockSyncStorage,
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(PrivacyComponent);
        component = fixture.componentInstance;
        privacyStatementService = TestBed.inject(PrivacyStatementService);
        languageHelper = TestBed.inject(JhiLanguageHelper);
        fixture.detectChanges();
    });

    it('should load privacy statement on init in correct language', () => {
        jest.spyOn(languageHelper, 'language', 'get').mockReturnValue(of('en'));
        const privacyServiceSpy = jest.spyOn(privacyStatementService, 'getPrivacyStatement');
        component.ngOnInit();
        fixture.detectChanges();
        expect(privacyServiceSpy).toHaveBeenCalledOnce();
        expect(privacyServiceSpy).toHaveBeenCalledWith(PrivacyStatementLanguage.ENGLISH);
    });
});
