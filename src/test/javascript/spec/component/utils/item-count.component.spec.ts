import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';

describe('ItemCountComponent test', () => {
    let comp: ItemCountComponent;
    let fixture: ComponentFixture<ItemCountComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [ItemCountComponent, TranslateDirective],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ItemCountComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('UI logic tests', () => {
        it('should initialize with zero', () => {
            expect(comp.itemRangeBegin).toBe(0);
            expect(comp.itemRangeEnd).toBe(0);
            expect(comp.itemTotal).toBe(0);
        });

        it('should set range from 0 to 0, total 0 if there are no elements', () => {
            // GIVEN
            comp.params = { page: 1, totalItems: 0, itemsPerPage: 10 };

            // THEN
            expect(comp.itemRangeBegin).toBe(0);
            expect(comp.itemRangeEnd).toBe(0);
            expect(comp.itemTotal).toBe(0);
        });

        it('should change the content on page change', () => {
            // GIVEN
            comp.params = { page: 1, totalItems: 100, itemsPerPage: 10 };

            // THEN
            expect(comp.itemRangeBegin).toBe(1);
            expect(comp.itemRangeEnd).toBe(10);
            expect(comp.itemTotal).toBe(100);

            // GIVEN
            comp.params = { page: 2, totalItems: 100, itemsPerPage: 10 };

            // THEN
            expect(comp.itemRangeBegin).toBe(11);
            expect(comp.itemRangeEnd).toBe(20);
            expect(comp.itemTotal).toBe(100);
        });

        it('should set the second number to totalItems if this is the last page which contains less than itemsPerPage items', () => {
            // GIVEN
            comp.params = { page: 2, totalItems: 16, itemsPerPage: 10 };

            // THEN
            expect(comp.itemRangeBegin).toBe(11);
            expect(comp.itemRangeEnd).toBe(16);
            expect(comp.itemTotal).toBe(16);
        });
    });
});
