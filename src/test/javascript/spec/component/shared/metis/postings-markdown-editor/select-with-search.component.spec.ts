import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { MatMenuModule } from '@angular/material/menu';
import { MatInputModule } from '@angular/material/input';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faAt } from '@fortawesome/free-solid-svg-icons';

import { SelectWithSearchComponent } from 'app/shared/markdown-editor/select-with-search/select-with-search.component';
import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

describe('SelectWithSearchComponent', () => {
    let component: SelectWithSearchComponent;
    let fixture: ComponentFixture<SelectWithSearchComponent>;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [SelectWithSearchComponent],
            imports: [HttpClientTestingModule, FormsModule, MatMenuModule, MatInputModule, NoopAnimationsModule, FontAwesomeModule],
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
            insertSelection: () => {},
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
        expect(component.focusInput).toBeTrue();

        // Simulate search input
        component.updateSearchTerm('test 1', true);
        tick(1);
        expect(component.values).toEqual([{ name: 'test 1' }]);
    }));

    it('should handle errors when performing search', fakeAsync(() => {
        const command: InteractiveSearchCommand = {
            setSelectWithSearchComponent: () => {},
            performSearch: () => throwError(new HttpErrorResponse({ status: 400 })),
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

    it('should open and close the menu', () => {
        // Find the menu button in the fixture
        const menuButton = fixture.debugElement.nativeElement.querySelector('button.btn.btn-sm.py-0');

        const handleMenuOpen = jest.spyOn(component, 'handleMenuOpen');
        const handleMenuClosed = jest.spyOn(component, 'handleMenuClosed');

        // Click button to open menu
        menuButton.click();
        fixture.detectChanges();

        // Assert that the handleMenuOpen method was called
        expect(handleMenuOpen).toHaveBeenCalled();
        expect(component.menuTrigger.menuOpen).toBeTrue();

        // Click button to close menu
        menuButton.click();
        fixture.detectChanges();

        // Assert that the handleMenuClosed method was called
        expect(handleMenuClosed).toHaveBeenCalled();
        expect(component.menuTrigger.menuOpen).toBeFalse();
    });

    it('should set the selectedValue and close the menu when calling fillSelection', () => {
        // Simulate some values in the component
        component.values = [{ name: 'Item 1' }, { name: 'Item 2' }];
        fixture.detectChanges();

        const setSelection = jest.spyOn(component, 'setSelection');

        component.fillSelection();

        // Assert that the setSelection method was called with the last item in the values array
        expect(setSelection).toHaveBeenCalledWith({ name: 'Item 2' });
        expect(component.selectedValue).toEqual({ name: 'Item 2' });

        // Assert that the menuTrigger is closed after calling fillSelection
        expect(component.menuTrigger.menuOpen).toBeFalse();
    });
});
