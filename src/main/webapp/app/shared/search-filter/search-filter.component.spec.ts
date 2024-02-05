import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { SearchFilterComponent } from './search-filter.component';

describe('SearchFilterComponent', () => {
    let component: SearchFilterComponent;
    let fixture: ComponentFixture<SearchFilterComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [SearchFilterComponent],
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
});
