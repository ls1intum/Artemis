import { Component, DebugElement } from '@angular/core';
import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { BrowserModule, By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TreeviewComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview/treeview.component';
import { TreeviewItemComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview-item/treeview-item.component';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { createGenericTestComponent, raiseInput } from './common';

interface FakeData {
    items?: TreeviewItem[];
    selectedChange: (data: any[]) => void;
}

const fakeData: FakeData = {
    items: undefined,
    selectedChange: () => {},
};

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'treeview-test',
    template: '',
})
class TestComponent {
    items = fakeData.items;
    selectedChange = fakeData.selectedChange;
}

const createTestComponent = (html: string) => createGenericTestComponent(html, TestComponent) as ComponentFixture<TestComponent>;

export function queryCheckboxAll(debugElement: DebugElement): DebugElement {
    return debugElement.query(By.css('.treeview-header input[type=checkbox]'));
}

export function queryFilterTextBox(debugElement: DebugElement): DebugElement {
    return debugElement.query(By.css('.treeview-header input[type=text]'));
}

export function queryCollapseExpandIcon(debugElement: DebugElement): DebugElement {
    return debugElement.query(By.css('.treeview-header i'));
}

export function queryDivider(debugElement: DebugElement): DebugElement {
    return debugElement.query(By.css('.treeview-header .dropdown-divider'));
}

export function queryItemCheckboxes(debugElement: DebugElement): DebugElement[] {
    return debugElement.queryAll(By.css('.treeview-container input[type=checkbox]'));
}

export function queryItemTexts(debugElement: DebugElement): string[] {
    const treeviewLabels = debugElement.queryAll(By.css('.treeview-container .form-check-label'));
    return treeviewLabels.map((label) => label.nativeElement.innerText.trim());
}

describe('TreeviewComponent', () => {
    const baseTemplate = '<treeview [items]="items" (selectedChange)="selectedChange($event)"></treeview>';
    let selectedChangeSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, BrowserModule],
            declarations: [TestComponent, TreeviewComponent, TreeviewItemComponent],
        });
        selectedChangeSpy = jest.spyOn(fakeData, 'selectedChange');
    });

    describe('no data binding', () => {
        let fixture: ComponentFixture<TestComponent>;

        beforeEach(fakeAsync(() => {
            fixture = createTestComponent('<treeview></treeview>');
            fixture.detectChanges();
            tick();
        }));

        it('should display "No items found"', () => {
            expect(fixture.nativeElement.textContent.trim()).toBe('No items found');
        });
    });

    describe('null data binding', () => {
        let fixture: ComponentFixture<TestComponent>;

        beforeEach(fakeAsync(() => {
            // @ts-ignore
            fakeData.items = null;
            fixture = createTestComponent('<treeview [items]="items"></treeview>');
            fixture.detectChanges();
            tick();
        }));

        it('should display "No items found"', () => {
            expect(fixture.nativeElement.textContent.trim()).toBe('No items found');
        });
    });

    describe('all items are collapsed but config has hasCollapseExpand=false', () => {
        let fixture: ComponentFixture<TestComponent>;

        beforeEach(fakeAsync(() => {
            fakeData.items = [new TreeviewItem({ text: '1', value: 1, collapsed: true })];
            fixture = createTestComponent('<treeview [items]="items"></treeview>');
            fixture.detectChanges();
            tick();
        }));

        it('should show icon on header with collapsed state', () => {
            const collapseExpandIcon = queryCollapseExpandIcon(fixture.debugElement);
            expect(collapseExpandIcon.nativeElement).toHaveClass('fa-expand');
        });
    });

    describe('config', () => {
        describe('maxHeight', () => {
            it('should display style correct max-height value', () => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
                const fixture = createTestComponent('<treeview [maxHeight]="400" [items]="items"></treeview>');
                fixture.detectChanges();
                const container = fixture.debugElement.query(By.css('.treeview-container'));
                expect(container.nativeElement).toHaveStyle({ 'max-height': '400px' });
            });
        });
    });

    describe('template', () => {
        let fixture: ComponentFixture<TestComponent>;

        beforeEach(() => {
            fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
        });

        describe('default template', () => {
            beforeEach(fakeAsync(() => {
                fixture = createTestComponent('<treeview [items]="items"></treeview>');
                fixture.detectChanges();
                tick();
            }));

            it('should work', () => {
                const treeviewTemplate = fixture.debugElement.query(By.css('.form-check'));
                expect(treeviewTemplate).not.toBeNull();
            });
        });

        describe('custom template', () => {
            beforeEach(fakeAsync(() => {
                const htmlTemplate = `
<ng-template #itemTemplate let-item="item"
    let-onCollapseExpand="onCollapseExpand"
    let-onCheckedChange="onCheckedChange">
    <div class="treeview-template">{{item.text}}</div>
</ng-template>
<treeview [items]="items" [itemTemplate]="itemTemplate"></treeview>`;
                fixture = createTestComponent(htmlTemplate);
                fixture.detectChanges();
                tick();
            }));

            it('should work', () => {
                const treeviewTemplate = fixture.debugElement.query(By.css('.treeview-template'));
                expect(treeviewTemplate.nativeElement.text).toBe('1');
            });
        });
    });

    describe('items', () => {
        let fixture: ComponentFixture<TestComponent>;
        let allCheckBox: DebugElement;
        let itemCheckBoxes: DebugElement[];
        let filterTextBox: DebugElement;

        beforeEach(() => {
            fakeData.items = [
                new TreeviewItem({
                    text: 'Item1',
                    value: 1,
                    children: [
                        {
                            text: 'Item11',
                            value: 11,
                            children: [
                                {
                                    text: 'Item111',
                                    value: 111,
                                },
                                {
                                    text: 'Item112',
                                    value: 112,
                                },
                            ],
                        },
                        {
                            text: 'Item12',
                            value: 12,
                        },
                    ],
                }),
                new TreeviewItem({
                    text: 'Item2',
                    value: 2,
                }),
            ];
        });

        beforeEach(fakeAsync(() => {
            selectedChangeSpy.mockReset();
            fixture = createTestComponent(baseTemplate);
            fixture.detectChanges();
            tick();
            allCheckBox = queryCheckboxAll(fixture.debugElement);
            itemCheckBoxes = queryItemCheckboxes(fixture.debugElement);
            filterTextBox = queryFilterTextBox(fixture.debugElement);
        }));

        it('should raise selectedChange when binding items', () => {
            expect(selectedChangeSpy).toHaveBeenCalledWith([111, 112, 12, 2]);
        });

        describe('uncheck "Item1"', () => {
            beforeEach(fakeAsync(() => {
                selectedChangeSpy.mockReset();
                itemCheckBoxes[0].nativeElement.click();
                fixture.detectChanges();
                tick();
            }));

            it('should raise event selectedChange', () => {
                expect(selectedChangeSpy).toHaveBeenCalledWith([2]);
            });

            describe('uncheck "Item2"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[itemCheckBoxes.length - 1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should raise event selectedChange', () => {
                    expect(selectedChangeSpy).toHaveBeenCalledWith([]);
                });
            });

            describe('check "Item11"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have 4 items are checked', () => {
                    const checkedItems = itemCheckBoxes.filter((item) => item.nativeElement.checked);
                    expect(checkedItems.length).toBe(4);
                });

                it('should raise event selectedChange', () => {
                    expect(selectedChangeSpy).toHaveBeenCalledWith([111, 112, 2]);
                });
            });

            describe('check "Item111"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[2].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have "Item1" is unchecked', () => {
                    expect(itemCheckBoxes[0].nativeElement.checked).toBeFalsy();
                });
            });
        });

        describe('collapse/expand icon', () => {
            let collapseExpandIcon: DebugElement;

            beforeEach(() => {
                collapseExpandIcon = queryCollapseExpandIcon(fixture.debugElement);
            });

            it('should have element class "fa-compress"', () => {
                expect(collapseExpandIcon.nativeElement).toHaveClass('fa-compress');
            });

            it('should display "Item1" & "Item2"', () => {
                const texts = queryItemTexts(fixture.debugElement);
                expect(texts).toEqual(['Item1', 'Item11', 'Item111', 'Item112', 'Item12', 'Item2']);
            });

            describe('toggle', () => {
                beforeEach(fakeAsync(() => {
                    collapseExpandIcon.nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have element class "fa-expand"', () => {
                    expect(collapseExpandIcon.nativeElement).toHaveClass('fa-expand');
                });

                it('should display "Item1" & "Item2"', () => {
                    const texts = queryItemTexts(fixture.debugElement);
                    expect(texts).toEqual(['Item1', 'Item2']);
                });
            });
        });
    });
});
