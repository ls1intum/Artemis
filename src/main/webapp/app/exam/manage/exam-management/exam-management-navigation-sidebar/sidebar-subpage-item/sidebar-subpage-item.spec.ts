import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SidebarSubpageItem } from './sidebar-subpage-item';

describe('SidebarSubpageItem', () => {
    let component: SidebarSubpageItem;
    let fixture: ComponentFixture<SidebarSubpageItem>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SidebarSubpageItem],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarSubpageItem);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
