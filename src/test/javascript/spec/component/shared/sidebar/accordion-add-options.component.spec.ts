import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AccordionAddOptionsComponent } from 'app/shared/sidebar/accordion-add-options/accordion-add-options.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';

describe('AccordionAddOptionsComponent', () => {
    let component: AccordionAddOptionsComponent;
    let fixture: ComponentFixture<AccordionAddOptionsComponent>;
    let sidebarEventService: SidebarEventService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbCollapseMocksModule, NgbTooltipMocksModule],
            declarations: [AccordionAddOptionsComponent, MockComponent(FaIconComponent)],
            providers: [MockProvider(NgbModal), MockProvider(SidebarEventService)],
        }).compileComponents();

        sidebarEventService = TestBed.inject(SidebarEventService);
        fixture = TestBed.createComponent(AccordionAddOptionsComponent);
        component = fixture.componentInstance;

        jest.spyOn(sidebarEventService, 'emitSidebarAccordionPlusClickedEvent').mockImplementation(() => {});
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call emitSidebarAccordionPlusClickedEvent when button is pressed', fakeAsync(() => {
        component.groupKey = 'test';
        fixture.detectChanges();
        const emitSidebarAccordionPlusClickedEventSpy = jest.spyOn(sidebarEventService, 'emitSidebarAccordionPlusClickedEvent');

        const dialogOpenButton = fixture.debugElement.nativeElement.querySelector('#plusButton-test');
        dialogOpenButton.click();
        tick(301);

        expect(emitSidebarAccordionPlusClickedEventSpy).toHaveBeenCalledWith('test');
    }));
});
