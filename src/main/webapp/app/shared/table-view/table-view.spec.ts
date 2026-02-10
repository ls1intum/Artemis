import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TableView } from './table-view';

describe('TableView', () => {
    let component: TableView;
    let fixture: ComponentFixture<TableView>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TableView],
        }).compileComponents();

        fixture = TestBed.createComponent(TableView);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
