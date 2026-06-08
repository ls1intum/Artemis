import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ItemCountComponent } from 'app/foundation/pagination/item-count.component';

describe('ItemCountComponent test', () => {
    setupTestBed({ zoneless: true });

    let comp: ItemCountComponent;
    let fixture: ComponentFixture<ItemCountComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), TranslateDirective],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ItemCountComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('UI logic tests', () => {
        it('should initialize with zero', () => {
            expect(comp.itemRangeBegin()).toBe(0);
            expect(comp.itemRangeEnd()).toBe(0);
            expect(comp.itemTotal()).toBe(0);
        });

        it('should set range from 0 to 0, total 0 if there are no elements', () => {
            // GIVEN
            fixture.componentRef.setInput('params', { page: 1, totalItems: 0, itemsPerPage: 10 });

            // THEN
            expect(comp.itemRangeBegin()).toBe(0);
            expect(comp.itemRangeEnd()).toBe(0);
            expect(comp.itemTotal()).toBe(0);
        });

        it('should change the content on page change', () => {
            // GIVEN
            fixture.componentRef.setInput('params', { page: 1, totalItems: 100, itemsPerPage: 10 });

            // THEN
            expect(comp.itemRangeBegin()).toBe(1);
            expect(comp.itemRangeEnd()).toBe(10);
            expect(comp.itemTotal()).toBe(100);

            // GIVEN
            fixture.componentRef.setInput('params', { page: 2, totalItems: 100, itemsPerPage: 10 });

            // THEN
            expect(comp.itemRangeBegin()).toBe(11);
            expect(comp.itemRangeEnd()).toBe(20);
            expect(comp.itemTotal()).toBe(100);
        });

        it('should set the second number to totalItems if this is the last page which contains less than itemsPerPage items', () => {
            // GIVEN
            fixture.componentRef.setInput('params', { page: 2, totalItems: 16, itemsPerPage: 10 });

            // THEN
            expect(comp.itemRangeBegin()).toBe(11);
            expect(comp.itemRangeEnd()).toBe(16);
            expect(comp.itemTotal()).toBe(16);
        });
    });

    describe('rendering', () => {
        // Guards the full reactive chain under zoneless: params input -> computed signals -> [translateValues] ->
        // child jhiTranslate directive effect -> rendered DOM. A logic-only assertion on the computeds would miss a
        // broken re-render here.
        it('should render the interpolated item count and update it when params change', () => {
            const translateService = TestBed.inject(TranslateService);
            translateService.setTranslation('en', { global: { 'item-count': '{{first}} - {{second}} of {{total}}' } });
            translateService.use('en');

            fixture.componentRef.setInput('params', { page: 2, totalItems: 16, itemsPerPage: 10 });
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('div').textContent).toContain('11 - 16 of 16');

            fixture.componentRef.setInput('params', { page: 1, totalItems: 100, itemsPerPage: 10 });
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('div').textContent).toContain('1 - 10 of 100');
        });
    });
});
