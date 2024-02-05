import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { InAppSidebarCardItemComponent } from './in-app-sidebar-card-item.component';

describe('InAppSidebarCardItemComponent', () => {
    let component: InAppSidebarCardItemComponent;
    let fixture: ComponentFixture<InAppSidebarCardItemComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [InAppSidebarCardItemComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(InAppSidebarCardItemComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
