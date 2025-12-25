import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockLanguageHelper } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';

describe('ImprintComponent', () => {
    let component: ImprintComponent;
    let fixture: ComponentFixture<ImprintComponent>;
    let legalDocumentService: LegalDocumentService;
    let languageHelper: JhiLanguageHelper;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ImprintComponent, MockDirective(TranslateDirective), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                SessionStorageService,
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(withFetch()),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ImprintComponent);
        component = fixture.componentInstance;
        legalDocumentService = TestBed.inject(LegalDocumentService);
        languageHelper = TestBed.inject(JhiLanguageHelper);
        fixture.detectChanges();
    });

    it('should load imprint on init in correct language', () => {
        jest.spyOn(languageHelper, 'language', 'get').mockReturnValue(of('en'));
        const imprintServiceSpy = jest.spyOn(legalDocumentService, 'getImprint');
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(imprintServiceSpy).toHaveBeenCalledOnce();
        expect(imprintServiceSpy).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
    });
});
