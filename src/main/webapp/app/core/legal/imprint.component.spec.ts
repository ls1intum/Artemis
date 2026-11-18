import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockDirective } from 'ng-mocks';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LegalDocumentLanguage } from 'app/admin/legal/legal-document.model';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockLanguageHelper } from 'test/helpers/mocks/service/mock-translate.service';
import { BehaviorSubject, of } from 'rxjs';
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
    let languageHelper: JhiLanguageHelper;
    let fragmentSubject: BehaviorSubject<string | null>;
    beforeEach(async () => {
        fragmentSubject = new BehaviorSubject<string | null>(null);
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
        languageHelper = TestBed.inject(JhiLanguageHelper);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load imprint on init in correct language', () => {
        vi.spyOn(languageHelper, 'language', 'get').mockReturnValue(of('en'));
        const imprintServiceSpy = vi.spyOn(legalDocumentService, 'getImprint');
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(imprintServiceSpy).toHaveBeenCalledOnce();
        expect(imprintServiceSpy).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
    });

    it('should retry fragment scrolling after markdown rendering completes', () => {
        vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 0);
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
