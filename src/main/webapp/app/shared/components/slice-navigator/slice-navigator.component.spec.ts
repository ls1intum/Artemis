import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PageChangeEvent, PaginationConfig, SliceNavigatorComponent } from './slice-navigator.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockComponent } from 'ng-mocks';

describe('SliceNavigatorComponent', () => {
    let component: SliceNavigatorComponent;
    let fixture: ComponentFixture<SliceNavigatorComponent>;

    const initialConfig: PaginationConfig = {
        pageSize: 10,
        initialPage: 1,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SliceNavigatorComponent, MockComponent(ButtonComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(SliceNavigatorComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('config', initialConfig);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with the correct page size and initial page from config', () => {
        expect(component.itemsPerPage()).toBe(initialConfig.pageSize);
        expect(component.currentPage()).toBe(initialConfig.initialPage);
    });

    it('should emit a page change event when nextPage is called', () => {
        let emittedEvent: PageChangeEvent | undefined;
        component.pageChange.subscribe((event: PageChangeEvent) => {
            emittedEvent = event;
        });

        component.nextPage();
        fixture.detectChanges();

        expect(emittedEvent).toEqual({
            page: initialConfig.initialPage! + 1,
            pageSize: initialConfig.pageSize,
            direction: 'next',
        });
    });

    it('should emit a page change event when previousPage is called', () => {
        let emittedEvent: PageChangeEvent | undefined;
        component.pageChange.subscribe((event: PageChangeEvent) => {
            emittedEvent = event;
        });

        component.nextPage(); // Go to page 2 first
        fixture.detectChanges();
        component.previousPage(); // Go back to page 1
        fixture.detectChanges();

        expect(emittedEvent).toEqual({
            page: initialConfig.initialPage,
            pageSize: initialConfig.pageSize,
            direction: 'previous',
        });
    });

    it('should disable the previous button when on the first page', () => {
        expect(component.previousDisabled()).toBeTrue();
    });

    it('should enable the previous button when not on the first page', () => {
        component.nextPage();
        fixture.detectChanges();
        expect(component.previousDisabled()).toBeFalse();
    });

    it('should disable the next button when hasMoreItems is false', () => {
        fixture.componentRef.setInput('hasMoreItems', false);
        fixture.detectChanges();
        expect(component.nextDisabled()).toBeTrue();
    });

    it('should disable the next button when isLoading is true', () => {
        fixture.componentRef.setInput('isLoading', true);
        fixture.detectChanges();
        expect(component.nextDisabled()).toBeTrue();
    });

    it('should not emit a page change event when nextPage is called and hasMoreItems is false', () => {
        let emittedEvent: PageChangeEvent | undefined;
        component.pageChange.subscribe((event: PageChangeEvent) => {
            emittedEvent = event;
        });

        fixture.componentRef.setInput('hasMoreItems', false);
        component.nextPage();
        fixture.detectChanges();

        expect(emittedEvent).toBeUndefined();
    });

    it('should not emit a page change event when previousPage is called and on the first page', () => {
        let emittedEvent: PageChangeEvent | undefined;
        component.pageChange.subscribe((event: PageChangeEvent) => {
            emittedEvent = event;
        });

        component.previousPage();
        fixture.detectChanges();

        expect(emittedEvent).toBeUndefined();
    });

    it('should update currentPage and itemsPerPage when config changes', () => {
        const newConfig: PaginationConfig = {
            pageSize: 20,
            initialPage: 3,
        };

        fixture.componentRef.setInput('config', newConfig);
        fixture.detectChanges();

        expect(component.itemsPerPage()).toBe(newConfig.pageSize);
        expect(component.currentPage()).toBe(newConfig.initialPage);
    });
});
