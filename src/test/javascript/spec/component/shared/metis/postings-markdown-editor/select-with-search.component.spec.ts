import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faAt } from '@fortawesome/free-solid-svg-icons';

import { SelectWithSearchComponent } from 'app/shared/markdown-editor/select-with-search/select-with-search.component';
import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { SimpleChange } from '@angular/core';

describe('SelectWithSearchComponent', () => {
    let component: SelectWithSearchComponent;
    let fixture: ComponentFixture<SelectWithSearchComponent>;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [SelectWithSearchComponent],
            imports: [HttpClientTestingModule, NgbDropdownModule, FontAwesomeModule],
            providers: [MockProvider(AlertService)],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SelectWithSearchComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        const testItems = [{ name: 'test 1' }, { name: 'test 2' }];
        const command: InteractiveSearchCommand = {
            setSelectWithSearchComponent: () => {},
            performSearch: (searchTerm: string) => {
                const filteredItems = testItems.filter((item) => item.name.includes(searchTerm));
                return of(new HttpResponse<any[]>({ body: filteredItems }));
            },
            buttonIcon: faAt,
            execute: () => {
                component.open();
            },
            insertSelection: () => {},
            getCursorScreenPosition: () => {
                return { pageX: 0, pageY: 0 };
            },
            updateSearchTerm: () => {},
        } as any;
        component.command = command;
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
    });

    it('should initialize and subscribe to search$', fakeAsync(() => {
        expect(component.values).toEqual([]);
        expect(component.selectedValue).toBeUndefined();

        // Simulate search input
        component.updateSearchTerm('test 1', true);
        tick(1);
        expect(component.values).toEqual([{ name: 'test 1' }]);
    }));

    it('should handle errors when performing search', fakeAsync(() => {
        const command: InteractiveSearchCommand = {
            setSelectWithSearchComponent: () => {},
            performSearch: () => throwError(() => new HttpErrorResponse({ status: 400 })),
            buttonIcon: faAt,
            insertSelection: () => {},
        } as any;

        component.command = command;
        fixture.detectChanges();

        const alertSpy = jest.spyOn(alertService, 'error');

        component.updateSearchTerm('test', true);
        tick(1);

        expect(alertSpy).toHaveBeenCalledOnce();
    }));

    it('should toggle menu when clicking button', () => {
        // Find the menu button in the fixture
        const menuButton = fixture.debugElement.nativeElement.querySelector('button.btn.btn-sm.py-0');

        const commandExecuteSpy = jest.spyOn(component.command, 'execute');
        const menuCloseSpy = jest.spyOn(component, 'close');

        // Click button to open menu
        menuButton.click();
        fixture.detectChanges();

        expect(commandExecuteSpy).toHaveBeenCalled();

        // Click button to close menu
        menuButton.click();
        fixture.detectChanges();

        expect(menuCloseSpy).toHaveBeenCalled();
    });

    it('should open and close the menu', () => {
        const handleMenuOpenSpy = jest.spyOn(component, 'handleMenuOpen');
        const handleMenuClosedSpy = jest.spyOn(component, 'handleMenuClosed');

        // Click button to open menu
        component.open();
        fixture.detectChanges();

        // Assert that the handleMenuOpen method was called
        expect(handleMenuOpenSpy).toHaveBeenCalledOnce();
        expect(component.dropdown.isOpen()).toBeTrue();

        // Click button to close menu
        component.close();
        fixture.detectChanges();

        // Assert that the handleMenuClosed method was called
        expect(handleMenuClosedSpy).toHaveBeenCalledOnce();
        expect(component.dropdown.isOpen()).toBeFalse();
    });

    it('should set the selectedValue and close the menu when calling setSelection', () => {
        // Simulate some values in the component
        component.values = [{ name: 'Item 1' }, { name: 'Item 2' }];
        fixture.detectChanges();

        component.setSelection({ name: 'Item 1' });

        expect(component.selectedValue).toEqual({ name: 'Item 1' });
        expect(component.dropdown.isOpen()).toBeFalse();
    });

    it('searchTerm updates on changes', () => {
        component.editorContentString = 'test';
        fixture.detectChanges();

        const updateSearchTermSpy = jest.spyOn(component.command, 'updateSearchTerm');

        component.ngOnChanges({ editorContentString: {} as SimpleChange });

        expect(updateSearchTermSpy).toHaveBeenCalledOnce();
    });
});
