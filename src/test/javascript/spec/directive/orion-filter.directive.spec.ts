import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
// @ts-ignore
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { isOrion } from 'app/shared/orion/orion';

@Component({
    selector: 'jhi-test-component',
    template: '<div id="shown" jhiOrionFilter [showInOrionWindow]="true"></div><div id="hidden" jhiOrionFilter [showInOrionWindow]="false"></div>',
})
class TestComponent {}

describe('OrionFilterDirective', () => {
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [TestComponent, OrionFilterDirective],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                debugElement = fixture.debugElement;
            });
    });

    it('should show/hide elements if isOrion is true', fakeAsync(() => {
        // @ts-ignore
        isOrion = true;

        fixture.detectChanges();
        tick();

        const shownDiv = debugElement.query(By.css('#shown'));
        expect(shownDiv).not.toBeNull();
        expect(shownDiv.nativeElement.style.display).toBe('');

        const hiddenDiv = debugElement.query(By.css('#hidden'));
        expect(hiddenDiv).not.toBeNull();
        expect(hiddenDiv.nativeElement.style.display).toBe('none');
    }));

    it('should show/hide elements if isOrion is false', fakeAsync(() => {
        // @ts-ignore
        isOrion = false;

        fixture.detectChanges();
        tick();

        const shownDiv = debugElement.query(By.css('#shown'));
        expect(shownDiv).not.toBeNull();
        expect(shownDiv.nativeElement.style.display).toBe('none');

        const hiddenDiv = debugElement.query(By.css('#hidden'));
        expect(hiddenDiv).not.toBeNull();
        expect(hiddenDiv.nativeElement.style.display).toBe('');
    }));
});
