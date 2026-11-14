import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockDirective } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LegalDocumentLanguage } from 'app/admin/legal/legal-document.model';
import { Imprint } from 'app/admin/legal/imprint.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockLanguageHelper } from 'test/helpers/mocks/service/mock-translate.service';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { ImprintComponent } from 'app/core/legal/imprint.component';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';

describe('ImprintComponent', () => {
    let component: ImprintComponent;
    let fixture: ComponentFixture<ImprintComponent>;
    let legalDocumentService: LegalDocumentService;
    let language: BehaviorSubject<string>;
    let fragmentSubject: BehaviorSubject<string | null>;
    beforeEach(async () => {
        fragmentSubject = new BehaviorSubject<string | null>(null);
        language = new BehaviorSubject<string>(LegalDocumentLanguage.ENGLISH);
        await TestBed.configureTestingModule({
            imports: [ImprintComponent, MockDirective(TranslateDirective), MockDirective(MarkdownDirective)],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                SessionStorageService,
                { provide: ActivatedRoute, useValue: Object.assign(new MockActivatedRoute(), { fragment: fragmentSubject }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ImprintComponent);
        component = fixture.componentInstance;
        legalDocumentService = TestBed.inject(LegalDocumentService);
        vi.spyOn(TestBed.inject(JhiLanguageHelper), 'language', 'get').mockReturnValue(language);
        vi.spyOn(legalDocumentService, 'getImprint').mockReturnValue(of(new Imprint(LegalDocumentLanguage.ENGLISH)));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load on init and refetch on language change without rendering a stale response', () => {
        const englishResponse = new Subject<Imprint>();
        const german = new Imprint(LegalDocumentLanguage.GERMAN);
        german.text = '# Deutscher Text';
        const fetchDocument = vi.mocked(legalDocumentService.getImprint).mockReturnValueOnce(englishResponse).mockReturnValueOnce(of(german));

        fixture.detectChanges();

        expect(fetchDocument).toHaveBeenCalledExactlyOnceWith(LegalDocumentLanguage.ENGLISH);
        language.next(LegalDocumentLanguage.GERMAN);
        expect(fetchDocument).toHaveBeenNthCalledWith(2, LegalDocumentLanguage.GERMAN);
        expect(englishResponse.observed).toBe(false);

        const stale = new Imprint(LegalDocumentLanguage.ENGLISH);
        stale.text = '# English text';
        englishResponse.next(stale);
        language.next('fr');
        fixture.detectChanges();

        expect(fetchDocument).toHaveBeenCalledTimes(2);
        expect(component.imprint()).toBe('# Deutscher Text');
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
