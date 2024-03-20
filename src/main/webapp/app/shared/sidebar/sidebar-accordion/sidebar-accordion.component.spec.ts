import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from 'src/test/javascript/spec/test.module';
import { SidebarAccordionComponent } from './sidebar-accordion.component';

fdescribe('SidebarAccordionComponent', () => {
    let component: SidebarAccordionComponent;
    let fixture: ComponentFixture<SidebarAccordionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SidebarAccordionComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarAccordionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should expand the first item when clicked', () => {
        const firstItemHeader = fixture.nativeElement.querySelector('#test-accordion-item-header');
        firstItemHeader.click();
        fixture.detectChanges();
        const firstItemContent = fixture.nativeElement.querySelector('#test-accordion-item-content');
        expect(firstItemContent.classList.contains('expanded')).toBe(true);
    });
});
