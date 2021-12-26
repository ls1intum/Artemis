import { Component, DebugElement } from '@angular/core';
import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { BrowserModule, By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { slice } from 'lodash-es';
import { TreeviewItemComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview-item/treeview-item.component';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { createGenericTestComponent } from './common';

const fakeItemTemplate = `
<ng-template #itemTemplate let-item="item"
  let-onCollapseExpand="onCollapseExpand"
  let-onCheckedChange="onCheckedChange">
  <div class="form-check">
    <i *ngIf="item.children" (click)="onCollapseExpand()" aria-hidden="true"
      class="fa" [class.fa-caret-right]="item.collapsed" [class.fa-caret-down]="!item.collapsed"></i>
    <label class="form-check-label">
      <input type="checkbox" class="form-check-input"
        [(ngModel)]="item.checked" (ngModelChange)="onCheckedChange()" [disabled]="item.disabled" />
      {{item.text}}
    </label>
  </div>
</ng-template>
`;

interface FakeData {
    item: TreeviewItem;
    checkedChange: (checked: boolean) => void;
}

const fakeData: FakeData = {
    // @ts-ignore
    item: undefined,
    checkedChange: () => {},
};

const testTemplate = fakeItemTemplate + '<treeview-item [item]="item" [template]="itemTemplate" (checkedChange)="checkedChange($event)"></treeview-item>';

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'test',
    template: '',
})
class TestComponent {
    item = fakeData.item;
    checkedChange = (checked: boolean) => fakeData.checkedChange(checked);
}

const createTestComponent = (html: string) => createGenericTestComponent(html, TestComponent) as ComponentFixture<TestComponent>;

describe('TreeviewItemComponent', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, BrowserModule],
            declarations: [TestComponent, TreeviewItemComponent],
        });
    });

    describe('item', () => {
        it('should not have element with class "treeview-item" if no item provided', () => {
            const fixture = createTestComponent('<treeview-item></treeview-item>');
            fixture.detectChanges();
            const element = fixture.debugElement.query(By.css('.treeview-item'));
            expect(element).toBeNull();
        });

        it('should have element with class "treeview-item" if binding item', () => {
            fakeData.item = new TreeviewItem({ text: '1', value: 1 });
            const fixture = createTestComponent('<treeview-item [item]="item"></treeview-item>');
            fixture.detectChanges();
            const element = fixture.debugElement.query(By.css('.treeview-item'));
            expect(element).not.toBeNull();
        });
    });

    describe('template', () => {
        let fixture: ComponentFixture<TestComponent>;
        let collapsedElement: DebugElement;
        let parentCheckbox: DebugElement;
        let childrenCheckboxes: DebugElement[];

        beforeEach(() => {
            fakeData.item = new TreeviewItem({
                text: 'Parent 1',
                value: 1,
                checked: true,
                collapsed: false,
                disabled: false,
                children: [
                    { text: 'Child 1', value: 11 },
                    { text: 'Child 2', value: 12 },
                ],
            });
        });

        beforeEach(fakeAsync(() => {
            fixture = createTestComponent(testTemplate);
            fixture.detectChanges();
            tick();
            collapsedElement = fixture.debugElement.query(By.css('.fa'));
            const checkboxElements = fixture.debugElement.queryAll(By.css('.form-check-input'));
            parentCheckbox = checkboxElements[0];
            childrenCheckboxes = slice(checkboxElements, 1);
        }));

        it('should work', () => {
            const textElement = fixture.debugElement.query(By.css('.form-check-label'));
            expect(textElement.nativeElement.innerText.trim()).toBe('Parent 1');
            expect(parentCheckbox.nativeElement.checked).toBeTruthy();
            expect(parentCheckbox.nativeElement.disabled).toBeFalsy();
            expect(collapsedElement.nativeElement.querySelector('#fa-caret-down')).not.toBeNull();
        });

        it('should render "Parent 1", "Child 1" & "Child 2" with checked', () => {
            expect(parentCheckbox.nativeElement.checked).toEqual(true);
            expect(childrenCheckboxes.map((element) => element.nativeElement.checked)).toEqual([true, true]);
        });

        describe('toggle collapse/expand', () => {
            beforeEach(fakeAsync(() => {
                collapsedElement.triggerEventHandler('click', {});
                fixture.detectChanges();
                tick();
            }));

            it('should invoke onCollapseExpand to change value of collapsed', () => {
                const cssClass = 'fa-caret-right';
                expect(collapsedElement.nativeElement.querySelector(`.${cssClass}`)).not.toBeNull();
            });

            it('should not render children', () => {
                const checkboxElements = fixture.debugElement.queryAll(By.css('.form-check-input'));
                expect(checkboxElements.length).toBe(1);
            });
        });

        describe('uncheck "Parent 1"', () => {
            let spy: jasmine.Spy;

            beforeEach(fakeAsync(() => {
                spy = spyOn(fakeData, 'checkedChange');
                parentCheckbox.nativeElement.click();
                fixture.detectChanges();
                tick();
            }));

            it('should un-check "Child 1" & "Child 2"', () => {
                expect(childrenCheckboxes.map((element) => element.nativeElement.checked)).toEqual([false, false]);
            });

            it('should raise event checkedChange', () => {
                expect(spy.calls.count()).toBe(1);
                const args = spy.calls.mostRecent().args;
                expect(args[0]).toBeFalsy();
            });
        });

        describe('un-check "Child 1"', () => {
            let spy: jasmine.Spy;

            beforeEach(fakeAsync(() => {
                spy = spyOn(fakeData, 'checkedChange');
                childrenCheckboxes[0].nativeElement.click();
                fixture.detectChanges();
                tick();
            }));

            it('should uncheck "Parent 1"', () => {
                expect(parentCheckbox.nativeElement.checked).toEqual(false);
            });

            it('should raise event checkedChange', () => {
                expect(spy.calls.count()).toBe(1);
                const args = spy.calls.mostRecent().args;
                expect(args[0]).toBeFalsy();
            });

            describe('un-check "Child 2"', () => {
                beforeEach(fakeAsync(() => {
                    spy.calls.reset();
                    childrenCheckboxes[1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should keep "Parent 1" unchecked', () => {
                    expect(parentCheckbox.nativeElement.checked).toEqual(false);
                });

                it('should raise event checkedChange', () => {
                    expect(spy.calls.count()).toBe(1);
                    const args = spy.calls.mostRecent().args;
                    expect(args[0]).toBeFalsy();
                });
            });

            describe('check "Child 1"', () => {
                beforeEach(fakeAsync(() => {
                    spy.calls.reset();
                    childrenCheckboxes[0].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should check "Parent 1"', () => {
                    expect(parentCheckbox.nativeElement.checked).toEqual(true);
                });

                it('should raise event checkedChange', () => {
                    expect(spy.calls.count()).toBe(1);
                    const args = spy.calls.mostRecent().args;
                    expect(args[0]).toBeTruthy();
                });
            });
        });
    });
});
