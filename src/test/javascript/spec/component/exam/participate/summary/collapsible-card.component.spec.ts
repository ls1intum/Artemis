import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { CollapsibleCardComponent } from 'app/exam/participate/summary/collapsible-card.component';
import { By } from '@angular/platform-browser';

let fixture: ComponentFixture<CollapsibleCardComponent>;
let component: CollapsibleCardComponent;

describe('CollapsibleCardComponent', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CollapsibleCardComponent);
                component = fixture.componentInstance;

                component.toggleCollapse = () => {};
                component.isCardContentCollapsed = false;
            });
    });

    it('should collapse and expand exercise when collapse button is clicked', fakeAsync(() => {
        const toggleCollapseSpy = jest.spyOn(component, 'toggleCollapse').mockImplementation(() => {});

        fixture.detectChanges();
        const toggleCollapseHeader = fixture.debugElement.query(By.css('.card-header'));

        expect(toggleCollapseHeader).not.toBeNull();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleCollapseSpy).toHaveBeenCalledOnce();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleCollapseSpy).toHaveBeenCalledTimes(2);
    }));
});
