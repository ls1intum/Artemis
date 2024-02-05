import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { InAppSidebarComponent } from './in-app-sidebar.component';

describe('InAppSidebarComponent', () => {
    let component: InAppSidebarComponent;
    let fixture: ComponentFixture<InAppSidebarComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [InAppSidebarComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(InAppSidebarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
