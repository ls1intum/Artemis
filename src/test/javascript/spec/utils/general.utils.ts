import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';

const expect = chai.expect;

export const getFocusedElement = (debugElement: DebugElement) => {
    const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
    expect(focusedElement).to.deep.equal(debugElement.nativeElement);
};
