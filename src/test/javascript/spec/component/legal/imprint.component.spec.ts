import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { MockLanguageHelper } from '../../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { ImprintService } from 'app/shared/service/imprint.service';

describe('ImprintComponent', () => {
    let component: ImprintComponent;
    let fixture: ComponentFixture<ImprintComponent>;
    let imprintService: ImprintService;
    let languageHelper: JhiLanguageHelper;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ImprintComponent, MockDirective(TranslateDirective), MockPipe(HtmlForMarkdownPipe)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ImprintComponent);
        component = fixture.componentInstance;
        imprintService = TestBed.inject(ImprintService);
        languageHelper = TestBed.inject(JhiLanguageHelper);
        fixture.detectChanges();
    });

    it('should load imprint on init in correct language', () => {
        jest.spyOn(languageHelper, 'language', 'get').mockReturnValue(of('en'));
        const imprintServiceSpy = jest.spyOn(imprintService, 'getImprint');
        component.ngOnInit();
        fixture.detectChanges();
        expect(imprintServiceSpy).toHaveBeenCalledOnce();
        expect(imprintServiceSpy).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
    });
});
