import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';

const expect = chai.expect;

export const getFocusedElement = (debugElement: DebugElement) => {
    const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
    expect(focusedElement).to.deep.equal(debugElement.nativeElement);
};

export const getElement = (debugElement: DebugElement, identifier: string) => {
    const element = debugElement.query(By.css(identifier));
    return element ? element.nativeElement : null;
};

export const expectElementToBeEnabled = (element: null | any) => {
    expect(element).to.exist;
    expect(element.disabled).to.be.false;
};

export const expectElementToBeDisabled = (element: null | any) => {
    expect(element).to.exist;
    expect(element.disabled).to.be.true;
};
