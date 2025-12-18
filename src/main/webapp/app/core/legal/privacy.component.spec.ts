import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockLanguageHelper } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient, withFetch } from '@angular/common/http';

describe('PrivacyComponent', () => {
    let component: PrivacyComponent;
    let fixture: ComponentFixture<PrivacyComponent>;
    let privacyStatementService: LegalDocumentService;
    let languageHelper: JhiLanguageHelper;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule],
            declarations: [PrivacyComponent, MockDirective(TranslateDirective), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                SessionStorageService,
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(withFetch()),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(PrivacyComponent);
        component = fixture.componentInstance;
        privacyStatementService = TestBed.inject(LegalDocumentService);
        languageHelper = TestBed.inject(JhiLanguageHelper);
        fixture.detectChanges();
    });

    it('should load privacy statement on init in correct language', () => {
        jest.spyOn(languageHelper, 'language', 'get').mockReturnValue(of('en'));
        const privacyServiceSpy = jest.spyOn(privacyStatementService, 'getPrivacyStatement');
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(privacyServiceSpy).toHaveBeenCalledOnce();
        expect(privacyServiceSpy).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
    });
});
