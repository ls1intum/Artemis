import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { faUser } from '@fortawesome/free-solid-svg-icons';

import { SidebarSubpageItem } from './sidebar-subpage-item';

describe('SidebarSubpageItem', () => {
    let component: SidebarSubpageItem;
    let fixture: ComponentFixture<SidebarSubpageItem>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SidebarSubpageItem],
            providers: [provideRouter([])],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarSubpageItem);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('icon', faUser);
        fixture.componentRef.setInput('title', 'Test Title');
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
