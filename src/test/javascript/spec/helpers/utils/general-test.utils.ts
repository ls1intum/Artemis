import { DebugElement, OnChanges, SimpleChange, SimpleChanges } from '@angular/core';
import { By } from '@angular/platform-browser';

export const getFocusedElement = (debugElement: DebugElement) => {
    const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
    expect(focusedElement).toEqual(debugElement.nativeElement);
};

export const getElement = (debugElement: DebugElement, identifier: string) => {
    const element = debugElement.query(By.css(identifier));
    return element ? element.nativeElement : null;
};

export const getElements = (debugElement: DebugElement, identifier: string) => {
    const elements = debugElement.queryAll(By.css(identifier));
    return elements ? elements.map((element) => element.nativeElement) : null;
};

export const expectElementToBeEnabled = (element: null | any) => {
    expect(element).not.toBeNull();
    expect(element.disabled).toBeFalse();
};

export const expectElementToBeDisabled = (element: null | any) => {
    expect(element).not.toBeNull();
    expect(element.disabled).toBeTrue();
};

/**
 * Construct a changes obj and trigger ngOnChanges of the provided comp.
 * @param comp Angular Component that implements OnChanges
 * @param changes object with data needed to construct SimpleChange objects.
 */
export const triggerChanges = (comp: OnChanges, ...changes: Array<{ property: string; currentValue: any; previousValue?: any; firstChange?: boolean }>) => {
    const simpleChanges: SimpleChanges = changes.reduce((acc, { property, currentValue, previousValue, firstChange = true }) => {
        return { ...acc, [property]: new SimpleChange(previousValue, currentValue, firstChange) };
    }, {});
    comp.ngOnChanges(simpleChanges);
};
