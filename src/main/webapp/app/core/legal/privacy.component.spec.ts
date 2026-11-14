import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockDirective } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockLanguageHelper, MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { LegalDocumentLanguage } from 'app/admin/legal/legal-document.model';
import { PrivacyStatement } from 'app/admin/legal/privacy-statement.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';

describe('PrivacyComponent', () => {
    let component: PrivacyComponent;
    let fixture: ComponentFixture<PrivacyComponent>;
    let privacyStatementService: LegalDocumentService;
    let language: BehaviorSubject<string>;
    let fragmentSubject: BehaviorSubject<string | null>;
    beforeEach(async () => {
        fragmentSubject = new BehaviorSubject<string | null>(null);
        language = new BehaviorSubject<string>(LegalDocumentLanguage.ENGLISH);
        await TestBed.configureTestingModule({
            imports: [RouterModule, PrivacyComponent, MockDirective(TranslateDirective), MockDirective(MarkdownDirective)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: ActivatedRoute, useValue: Object.assign(new MockActivatedRoute(), { fragment: fragmentSubject }) },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(PrivacyComponent);
        component = fixture.componentInstance;
        privacyStatementService = TestBed.inject(LegalDocumentService);
        vi.spyOn(TestBed.inject(JhiLanguageHelper), 'language', 'get').mockReturnValue(language);
        vi.spyOn(privacyStatementService, 'getPrivacyStatement').mockReturnValue(of(new PrivacyStatement(LegalDocumentLanguage.ENGLISH)));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load on init and refetch on language change without rendering a stale response', () => {
        vi.spyOn(TestBed.inject(AccountService), 'isAuthenticated').mockReturnValue(false);
        const englishResponse = new Subject<PrivacyStatement>();
        const german = new PrivacyStatement(LegalDocumentLanguage.GERMAN);
        german.text = '# Deutscher Text';
        const fetchDocument = vi.mocked(privacyStatementService.getPrivacyStatement).mockReturnValueOnce(englishResponse).mockReturnValueOnce(of(german));

        fixture.detectChanges();

        expect(fetchDocument).toHaveBeenCalledExactlyOnceWith(LegalDocumentLanguage.ENGLISH);
        language.next(LegalDocumentLanguage.GERMAN);
        expect(fetchDocument).toHaveBeenNthCalledWith(2, LegalDocumentLanguage.GERMAN);
        expect(englishResponse.observed).toBe(false);

        const stale = new PrivacyStatement(LegalDocumentLanguage.ENGLISH);
        stale.text = '# English text';
        englishResponse.next(stale);
        language.next('fr');
        fixture.detectChanges();

        expect(fetchDocument).toHaveBeenCalledTimes(2);
        expect(component.privacyStatement()).toBe('# Deutscher Text');
        expect(fixture.debugElement.query(By.css('a[href="/privacy/data-exports"]'))).toBeNull();
    });

    it('should show the data export link when signed in', () => {
        vi.spyOn(TestBed.inject(AccountService), 'isAuthenticated').mockReturnValue(true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('a[href="/privacy/data-exports"]'))).not.toBeNull();
    });

    it('should retry fragment scrolling after markdown rendering completes', () => {
        vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 0);
        fixture.detectChanges();
        fragmentSubject.next('delayed-fragment');
        const fragment = document.createElement('div');
        fragment.id = 'delayed-fragment';
        fragment.scrollIntoView = vi.fn();
        document.body.append(fragment);

        fixture.debugElement.query(By.css('div')).triggerEventHandler('markdownRendered');

        expect(fragment.scrollIntoView).toHaveBeenCalledOnce();
        fragment.remove();
    });
});
