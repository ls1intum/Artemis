import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { SearchFilterComponent } from '../../../../../main/webapp/app/shared/search-filter/search-filter.component';
import { MockModule, MockPipe } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('SearchFilterComponent', () => {
    let component: SearchFilterComponent;
    let fixture: ComponentFixture<SearchFilterComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ReactiveFormsModule), MockModule(FormsModule), MockModule(FontAwesomeModule)],
            declarations: [SearchFilterComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SearchFilterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call resetSearchValue when the reset icon is clicked', () => {
        component.filterForm.controls['searchFilter'].setValue('test search');
        fixture.detectChanges();
        jest.spyOn(component, 'resetSearchValue');

        const resetIcon = fixture.debugElement.query(By.css('#test-fa-times')).nativeElement;
        resetIcon.click();
        fixture.detectChanges();

        expect(component.resetSearchValue).toHaveBeenCalled();
        expect(component.filterForm.value.searchFilter).toBeNull();
    });

    it('should emit the search value on keyup', () => {
        jest.spyOn(component.newSearchEvent, 'emit');

        // Assuming the form control is named 'searchFilter' and bound to an input field
        const inputElement = fixture.debugElement.query(By.css('input')).nativeElement;
        inputElement.dispatchEvent(new Event('keyup'));
        fixture.detectChanges();

        expect(component.newSearchEvent.emit).toHaveBeenCalled();
    });
});
