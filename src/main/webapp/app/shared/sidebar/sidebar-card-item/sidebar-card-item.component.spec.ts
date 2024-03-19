import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { SidebarCardItemComponent } from './sidebar-card-item.component';

describe('SidebarCardItemComponent', () => {
    let component: SidebarCardItemComponent;
    let fixture: ComponentFixture<SidebarCardItemComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [SidebarCardItemComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardItemComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
