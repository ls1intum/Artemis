import { Component, DebugElement, OnChanges, SimpleChange, SimpleChanges } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ComponentFixture } from '@angular/core/testing';

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

/**
 * Retrieves the component instance from a fixture using the component's selector.
 *
 * @param fixture The Angular component fixture.
 * @param component The component class or instance.
 * @returns The component instance.
 * @throws Error if the selector is not found or if the element is not found.
 */
export function getComponentInstanceFromFixture<T>(fixture: ComponentFixture<any>, component: T) {
    const selector = getSelectorOfComponent(component);
    if (!selector) {
        throw new Error(`Selector not found for component ${component}`);
    }
    const element = fixture.debugElement.query(By.css(selector));
    if (!element) {
        throw new Error(`Element not found for selector ${selector}`);
    }
    return element.componentInstance as T;
}

/**
 * Extracts the selector of an Angular component from its metadata.
 *
 * @param component The component class from which to extract the selector.
 * @returns The selector string if found, otherwise undefined.
 */
export const getSelectorOfComponent = (component: any): string | undefined => {
    const metadata = (component as any).ɵcmp;

    if (metadata && metadata.selectors && metadata.selectors.length && metadata.selectors[0] && metadata.selectors[0].length && typeof metadata.selectors[0][0] === 'string') {
        return metadata.selectors[0][0];
    }

    return undefined;
};
