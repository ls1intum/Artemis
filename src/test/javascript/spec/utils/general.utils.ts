import { DebugElement, OnChanges, SimpleChanges, SimpleChange } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';

const expect = chai.expect;

export const getFocusedElement = (debugElement: DebugElement) => {
    const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
    expect(focusedElement).to.deep.equal(debugElement.nativeElement);
};

/**
 * Construct a changes obj and trigger ngOnChanges of the provided comp.
 * @param comp Angular Component that implements OnChanges
 * @param changes object with data needed to construct SimpleChange objects.
 */
export const triggerChanges = <T>(comp: OnChanges, ...changes: Array<{ property: string; newObj: T; oldObj?: T; firstChange?: boolean }>) => {
    const simpleChanges: SimpleChanges = changes.reduce((acc, { property, newObj, oldObj, firstChange = true }) => {
        return { ...acc, [property]: new SimpleChange(oldObj, newObj, firstChange) };
    }, {});
    comp.ngOnChanges(simpleChanges);
};
