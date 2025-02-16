import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

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
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(LegalDocumentService),
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
        fixture.detectChanges();
        expect(privacyServiceSpy).toHaveBeenCalledOnce();
        expect(privacyServiceSpy).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
    });
});
