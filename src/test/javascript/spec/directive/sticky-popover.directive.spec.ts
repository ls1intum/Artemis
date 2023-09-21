import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Component, DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../test.module';
import { By } from '@angular/platform-browser';
import { StickyPopoverDirective } from 'app/shared/sticky-popover/sticky-popover.directive';

@Component({
    template: '<div [jhiStickyPopover]="content" placement="right" triggers="manual"></div><ng-template #content><span>some content</span></ng-template>',
})
class StickyPopoverComponent {
    pattern: string;
}

describe('StickyPopoverDirective', () => {
    let fixture: ComponentFixture<StickyPopoverComponent>;
    let debugDirective: DebugElement;
    let directive: StickyPopoverDirective;
    let openStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [StickyPopoverDirective, StickyPopoverComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StickyPopoverComponent);
                debugDirective = fixture.debugElement.query(By.directive(StickyPopoverDirective));
                directive = debugDirective.injector.get(StickyPopoverDirective);
                openStub = jest.spyOn(directive, 'open');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open on hover', fakeAsync(() => {
        fixture.whenStable();
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        tick(10);
        expect(openStub).toHaveBeenCalledOnce();
        expect(directive.isOpen()).toBeTruthy();
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    }));

    it('should display content on hover', fakeAsync(() => {
        fixture.whenStable();
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        tick(10);
        const span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
    }));

    it('should close on leave', fakeAsync(() => {
        fixture.whenStable();
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerenter'));
        tick(10);
        let span = fixture.debugElement.query(By.css('span'));
        expect(span).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('pointerleave'));
        tick(100);
        span = fixture.debugElement.query(By.css('span'));
        expect(span).toBeNull();
    }));
});
