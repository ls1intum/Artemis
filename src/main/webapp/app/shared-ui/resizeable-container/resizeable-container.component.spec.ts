import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';

describe('ResizeableContainerComponent', () => {
    let component: ResizeableContainerComponent;
    let fixture: ComponentFixture<ResizeableContainerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({}).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ResizeableContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should let the left side scroll on its own only beside the right panel', () => {
        const left = (): HTMLElement => fixture.nativeElement.querySelector('[data-testid="resizeable-container-left"]');
        expect(left().classList).toContain('left--scrolls');

        // Without the right panel, e.g. in the course view, the surrounding page scrolls instead
        fixture.componentRef.setInput('showRightPanel', false);
        fixture.detectChanges();
        expect(left().classList).not.toContain('left--scrolls');
    });
});
