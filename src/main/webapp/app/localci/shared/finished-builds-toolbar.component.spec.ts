import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular/button';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { BuildJobSearchComponent } from './build-job-search.component';
import { FinishedBuildsToolbarComponent } from './finished-builds-toolbar.component';

describe('FinishedBuildsToolbarComponent', () => {
    let fixture: ComponentFixture<FinishedBuildsToolbarComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(FinishedBuildsToolbarComponent);
        fixture.detectChanges();
    });

    it('forwards edited search terms and search requests separately from refreshing', () => {
        const searchTermChanged = vi.fn();
        const searchRequested = vi.fn();
        const refreshRequested = vi.fn();
        fixture.componentInstance.searchTerm.subscribe(searchTermChanged);
        fixture.componentInstance.searchRequested.subscribe(searchRequested);
        fixture.componentInstance.refreshRequested.subscribe(refreshRequested);
        fixture.componentRef.setInput('searchTerm', 'initial');
        fixture.detectChanges();
        const search = fixture.debugElement.query(By.directive(BuildJobSearchComponent)).componentInstance as BuildJobSearchComponent;
        expect(search.searchTerm()).toBe('initial');
        search.searchTerm.set('updated');
        search.searchRequested.emit();
        fixture.detectChanges();
        expect(searchTermChanged).toHaveBeenCalledWith('updated');
        expect(searchRequested).toHaveBeenCalledOnce();
        expect(refreshRequested).not.toHaveBeenCalled();
        fixture.nativeElement.querySelector('[data-testid="refresh-builds"] button').click();
        expect(refreshRequested).toHaveBeenCalledOnce();
    });

    it('shows loading and the filter count without disabling the toolbar actions', () => {
        const filterRequested = vi.fn();
        fixture.componentInstance.filterRequested.subscribe(filterRequested);
        expect(fixture.nativeElement.querySelector('[data-testid="builds-loading"]')).toBeNull();
        fixture.componentRef.setInput('loading', true);
        fixture.componentRef.setInput('appliedFilterCount', 3);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="builds-loading"]')).not.toBeNull();
        const filter = fixture.debugElement.query(By.css('[data-testid="filter-builds"]')).componentInstance as TumAetUiButtonComponent;
        expect(filter.severity()).toBe('success');
        expect(filter.ariaLabel()).toBe('artemisApp.buildQueue.filter.open');
        const label = fixture.debugElement.query(By.css('[data-testid="filter-builds"] span[jhiTranslate]')).injector.get(TranslateDirective);
        expect(label.translateValues()).toEqual({ num: 3 });
        fixture.nativeElement.querySelector('[data-testid="filter-builds"] button').click();
        expect(filterRequested).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('appliedFilterCount', 0);
        fixture.detectChanges();
        expect(filter.severity()).toBe('secondary');
    });
});
