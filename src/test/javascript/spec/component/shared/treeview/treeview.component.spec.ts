import { Component, DebugElement } from '@angular/core';
import { TestBed, ComponentFixture, fakeAsync, tick, async } from '@angular/core/testing';
import { BrowserModule, By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TreeviewComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview/treeview.component';
import { TreeviewItemComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview-item/treeview-item.component';
import { TreeviewConfig } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-config';
import { TreeviewI18n, DefaultTreeviewI18n } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-i18n';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { TreeviewEventParser, DefaultTreeviewEventParser } from 'app/exercises/programming/shared/code-editor/treeview/helpers/treeview-event-parser';
import { createGenericTestComponent } from './common';

interface FakeData {
    config?: TreeviewConfig;
    items?: TreeviewItem[];
    selectedChange: (data: any[]) => void;
}

const fakeData: FakeData = {
    config: undefined,
    items: undefined,
    selectedChange: (data: any[]) => {},
};

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'ngx-test',
    template: '',
})
class TestComponent {
    config = fakeData.config;
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
    const baseTemplate = '<treeview [items]="items" [config]="config" (selectedChange)="selectedChange($event)"></treeview>';
    let selectedChangeSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, BrowserModule],
            declarations: [TestComponent, TreeviewComponent, TreeviewItemComponent],
            providers: [TreeviewConfig, { provide: TreeviewI18n, useClass: DefaultTreeviewI18n }, { provide: TreeviewEventParser, useClass: DefaultTreeviewEventParser }],
        });
        selectedChangeSpy = jest.spyOn(fakeData, 'selectedChange');
    });

    it('should initialize with default config', () => {
        const defaultConfig = new TreeviewConfig();
        const component = TestBed.createComponent(TreeviewComponent).componentInstance;
        expect(component.config).toEqual(defaultConfig);
    });

    describe('raiseSelectedChange', () => {
        let spy: jest.SpyInstance;

        beforeEach(fakeAsync(() => {
            const fixture = TestBed.createComponent(TreeviewComponent);
            spy = jest.spyOn(fixture.componentInstance.selectedChange, 'emit');
            fixture.componentInstance.raiseSelectedChange();
            fixture.detectChanges();
            tick();
        }));

        it('should raise event selectedChange', () => {
            expect(spy.mock.calls.any()).toBeTruthy();
            const args = spy.mock.calls.last().args;
            expect(args[0]).toEqual([]);
        });
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
            fakeData.config = TreeviewConfig.create({
                hasCollapseExpand: true,
            });
            fakeData.items = [new TreeviewItem({ text: '1', value: 1, collapsed: true })];
            fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
            fixture.detectChanges();
            tick();
        }));

        it('should show icon on header with collapsed state', () => {
            const collapseExpandIcon = queryCollapseExpandIcon(fixture.debugElement);
            expect(collapseExpandIcon.nativeElement).toHaveCssClass('fa-expand');
        });
    });

    describe('config', () => {
        describe('hasAllCheckBox', () => {
            beforeEach(() => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
            });

            it('should display checkbox "All" if value is true', () => {
                fakeData.config = TreeviewConfig.create({
                    hasAllCheckBox: true,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const checkboxAll = queryCheckboxAll(fixture.debugElement);
                expect(checkboxAll).not.toBeNull();
            });

            it('should not display checkbox "All" if value is false', () => {
                fakeData.config = TreeviewConfig.create({
                    hasAllCheckBox: false,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const checkboxAll = queryCheckboxAll(fixture.debugElement);
                expect(checkboxAll).toBeNull();
            });
        });

        describe('hasFilter', () => {
            beforeEach(() => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
            });

            it('should display checkbox Filter textbox if value is true', () => {
                fakeData.config = TreeviewConfig.create({
                    hasFilter: true,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const filterTextBox = queryFilterTextBox(fixture.debugElement);
                expect(filterTextBox).not.toBeNull();
            });

            it('should not display checkbox Filter textbox if value is false', () => {
                fakeData.config = TreeviewConfig.create({
                    hasFilter: false,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const filterTextBox = queryFilterTextBox(fixture.debugElement);
                expect(filterTextBox).toBeNull();
            });
        });

        describe('hasCollapseExpand', () => {
            beforeEach(() => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
            });

            it('should display icon Collapse/Expand if value is true', () => {
                fakeData.config = TreeviewConfig.create({
                    hasCollapseExpand: true,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const collapseExpandIcon = queryCollapseExpandIcon(fixture.debugElement);
                expect(collapseExpandIcon).not.toBeNull();
            });

            it('should not display icon Collapse/Expand if value is false', () => {
                fakeData.config = TreeviewConfig.create({
                    hasCollapseExpand: false,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const collapseExpandIcon = queryCollapseExpandIcon(fixture.debugElement);
                expect(collapseExpandIcon).toBeNull();
            });
        });

        describe('decoupleChildFromParent with false value', () => {
            let fixture: ComponentFixture<TestComponent>;
            let parentCheckbox: DebugElement;
            let childCheckbox: DebugElement;

            beforeEach(fakeAsync(() => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1, children: [{ text: '11', value: 11 }] })];
                fakeData.config = TreeviewConfig.create({
                    hasAllCheckBox: false,
                    hasCollapseExpand: false,
                    hasFilter: false,
                    decoupleChildFromParent: true,
                });
                fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                tick();
                const checkboxes = queryItemCheckboxes(fixture.debugElement);
                parentCheckbox = checkboxes[0];
                childCheckbox = checkboxes[1];
            }));

            it('should have checked state is true for child item', () => {
                expect(childCheckbox.nativeElement.checked).toBeTruthy();
            });

            describe('uncheck parent', () => {
                beforeEach(fakeAsync(() => {
                    parentCheckbox.nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should not change checked state of child item', () => {
                    expect(childCheckbox.nativeElement.checked).toBeTruthy();
                });
            });
        });

        describe('maxHeight', () => {
            it('should display style correct max-height value', () => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
                fakeData.config = TreeviewConfig.create({
                    maxHeight: 400,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const container = fixture.debugElement.query(By.css('.treeview-container'));
                expect(container.nativeElement).toHaveCssStyle({ 'max-height': '400px' });
            });
        });

        describe('divider', () => {
            beforeEach(() => {
                fakeData.items = [new TreeviewItem({ text: '1', value: 1 })];
            });

            it('should display divider with default config', () => {
                fakeData.config = new TreeviewConfig();
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const divider = queryDivider(fixture.debugElement);
                expect(divider).not.toBeNull();
            });

            it('should not display divider when no filter, no All checkbox & no collapse/expand', () => {
                fakeData.config = TreeviewConfig.create({
                    hasAllCheckBox: false,
                    hasCollapseExpand: false,
                    hasFilter: false,
                });
                const fixture = createTestComponent('<treeview [config]="config" [items]="items"></treeview>');
                fixture.detectChanges();
                const divider = queryDivider(fixture.debugElement);
                expect(divider).toBeNull();
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
                expect(treeviewTemplate.nativeElement).toHaveText('1');
            });
        });
    });

    describe('items', () => {
        let fixture: ComponentFixture<TestComponent>;
        let allCheckBox: DebugElement;
        let itemCheckBoxes: DebugElement[];
        let filterTextBox: DebugElement;

        beforeEach(() => {
            fakeData.config = TreeviewConfig.create({
                hasAllCheckBox: true,
                hasCollapseExpand: true,
                hasFilter: true,
                decoupleChildFromParent: false,
                maxHeight: 400,
            });
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
            expect(selectedChangeSpy.mock.calls.any()).toBeTruthy();
            const args = selectedChangeSpy.mock.calls.last().args;
            expect(args[0]).toEqual([111, 112, 12, 2]);
        });

        it('should have "All" checkbox is checked', () => {
            expect(allCheckBox.nativeElement.checked).toBeTruthy();
        });

        it('should have all of items are checked', () => {
            const checkedItems = itemCheckBoxes.filter((item) => item.nativeElement.checked);
            expect(checkedItems.length).toBe(itemCheckBoxes.length);
        });

        describe('uncheck "All"', () => {
            beforeEach(fakeAsync(() => {
                selectedChangeSpy.mockReset();
                allCheckBox.nativeElement.click();
                fixture.detectChanges();
                tick();
            }));

            it('should uncheck all of items', () => {
                const checkedItems = itemCheckBoxes.filter((item) => item.nativeElement.checked);
                expect(checkedItems.length).toBe(0);
            });

            it('should raise event selectedChange', () => {
                expect(selectedChangeSpy.mock.calls.any()).toBeTruthy();
                const args = selectedChangeSpy.mock.calls.last().args;
                expect(args[0]).toEqual([]);
            });
        });

        describe('uncheck "Item1"', () => {
            beforeEach(fakeAsync(() => {
                selectedChangeSpy.mockReset();
                itemCheckBoxes[0].nativeElement.click();
                fixture.detectChanges();
                tick();
            }));

            it('should have "All" checkbox is unchecked', () => {
                expect(allCheckBox.nativeElement.checked).toBeFalsy();
            });

            it('should raise event selectedChange', () => {
                expect(selectedChangeSpy.mock.calls.any()).toBeTruthy();
                const args = selectedChangeSpy.mock.calls.last().args;
                expect(args[0]).toEqual([2]);
            });

            describe('filtering "em2"', () => {
                beforeEach(fakeAsync(() => {
                    eventHelper.raiseInput(filterTextBox.nativeElement, 'em2');
                    fixture.detectChanges();
                    tick();
                }));

                it('should not display item "Item1" & its children', () => {
                    const texts = queryItemTexts(fixture.debugElement);
                    expect(texts).toEqual(['Item2']);
                });

                it('should have checkbox "All" is checked', () => {
                    expect(allCheckBox.nativeElement.checked).toBeTruthy();
                });
            });

            describe('filtering "em1"', () => {
                let itemCheckboxes: DebugElement[];

                beforeEach(fakeAsync(() => {
                    itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                    eventHelper.raiseInput(filterTextBox.nativeElement, 'em1');
                    fixture.detectChanges();
                    tick();
                }));

                it('should not display item "Item2"', () => {
                    const texts = queryItemTexts(fixture.debugElement);
                    expect(texts).toEqual(['Item1', 'Item11', 'Item111', 'Item112', 'Item12']);
                });

                it('should have checkbox "All" is unchecked', () => {
                    expect(allCheckBox.nativeElement.checked).toBeFalsy();
                });

                it('should have "Item11" is unchecked', () => {
                    expect(itemCheckBoxes[1].nativeElement.checked).toBeFalsy();
                });

                describe('check "Item11"', () => {
                    beforeEach(fakeAsync(() => {
                        itemCheckBoxes[1].nativeElement.click();
                        fixture.detectChanges();
                        tick();
                    }));

                    it('should have "Item1" is unchecked', () => {
                        expect(itemCheckBoxes[0].nativeElement.checked).toBeFalsy();
                    });

                    it('should have checkbox "All" is unchecked', () => {
                        expect(allCheckBox.nativeElement.checked).toBeFalsy();
                    });
                });
            });

            describe('uncheck "Item2"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[itemCheckBoxes.length - 1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should keep "All" checkbox is unchecked', () => {
                    expect(allCheckBox.nativeElement.checked).toBeFalsy();
                });

                it('should raise event selectedChange', () => {
                    expect(selectedChangeSpy.mock.calls.any()).toBeTruthy();
                    const args = selectedChangeSpy.mock.calls.last().args;
                    expect(args[0]).toEqual([]);
                });
            });

            describe('check "Item11"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have "All" checkbox is unchecked', () => {
                    expect(allCheckBox.nativeElement.checked).toBeFalsy();
                });

                it('should have 4 items are checked', () => {
                    const checkedItems = itemCheckBoxes.filter((item) => item.nativeElement.checked);
                    expect(checkedItems.length).toBe(4);
                });

                it('should raise event selectedChange', () => {
                    expect(selectedChangeSpy.mock.calls.any()).toBeTruthy();
                    const args = selectedChangeSpy.mock.calls.last().args;
                    expect(args[0]).toEqual([111, 112, 2]);
                });
            });

            describe('check "Item111"', () => {
                beforeEach(fakeAsync(() => {
                    selectedChangeSpy.mockReset();
                    itemCheckBoxes[2].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have "All" checkbox is unchecked', () => {
                    expect(allCheckBox.nativeElement.checked).toBeFalsy();
                });

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
                expect(collapseExpandIcon.nativeElement).toHaveCssClass('fa-compress');
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
                    expect(collapseExpandIcon.nativeElement).toHaveCssClass('fa-expand');
                });

                it('should display "Item1" & "Item2"', () => {
                    const texts = queryItemTexts(fixture.debugElement);
                    expect(texts).toEqual(['Item1', 'Item2']);
                });
            });
        });

        describe('filtering "em1"', () => {
            beforeEach(fakeAsync(() => {
                eventHelper.raiseInput(filterTextBox.nativeElement, 'em1');
                fixture.detectChanges();
                tick();
            }));

            it('should not display item "Item2"', () => {
                const texts = queryItemTexts(fixture.debugElement);
                expect(texts).toEqual(['Item1', 'Item11', 'Item111', 'Item112', 'Item12']);
            });

            it('should have checkbox "All" is checked', () => {
                expect(allCheckBox.nativeElement.checked).toBeTruthy();
            });

            describe('uncheck "Item1"', () => {
                let itemCheckboxes: DebugElement[];

                beforeEach(fakeAsync(() => {
                    itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                    itemCheckboxes[0].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should change checked value of checkbox "All" to false', () => {
                    expect(allCheckBox.nativeElement.checked).toBeFalsy();
                });

                it('should have checked value of "Item1" && its children are false', () => {
                    itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                    const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                    expect(checkedValues).toEqual([false, false, false, false, false]);
                });

                describe('clear filter', () => {
                    beforeEach(fakeAsync(() => {
                        itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                        eventHelper.raiseInput(filterTextBox.nativeElement, '');
                        fixture.detectChanges();
                        tick();
                    }));

                    it('should have checked of "Item1" is false', () => {
                        itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                        const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                        expect(checkedValues).toEqual([false, false, false, false, false, true]);
                    });

                    it('should have checked value of "All" checkbox is false', () => {
                        expect(allCheckBox.nativeElement.checked).toBeFalsy();
                    });

                    describe('check "All"', () => {
                        beforeEach(fakeAsync(() => {
                            allCheckBox.nativeElement.click();
                            fixture.detectChanges();
                            tick();
                        }));

                        it('should have checked value of "Item1" is true', () => {
                            itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                            const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                            expect(checkedValues).toEqual([true, true, true, true, true, true]);
                        });
                    });
                });
            });
        });

        describe('filtering "em11"', () => {
            beforeEach(fakeAsync(() => {
                eventHelper.raiseInput(filterTextBox.nativeElement, 'em11');
                fixture.detectChanges();
                tick();
            }));

            it('should display only "Item1" & its children', () => {
                const texts = queryItemTexts(fixture.debugElement);
                expect(texts).toEqual(['Item1', 'Item11', 'Item111', 'Item112']);
            });

            describe('uncheck "Item11', () => {
                let itemCheckboxes: DebugElement[];

                beforeEach(fakeAsync(() => {
                    itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                    itemCheckboxes[1].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have "Item1" is unchecked', () => {
                    expect(itemCheckboxes[0].nativeElement.checked).toBeFalsy();
                });

                describe('clear filter', () => {
                    beforeEach(fakeAsync(() => {
                        eventHelper.raiseInput(filterTextBox.nativeElement, '');
                        fixture.detectChanges();
                        tick();
                    }));

                    it('should have "Item12" & "Item2" are checked', () => {
                        itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                        const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                        expect(checkedValues).toEqual([false, false, false, false, true, true]);
                    });
                });

                describe('check "Item11"', () => {
                    beforeEach(fakeAsync(() => {
                        itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                        itemCheckboxes[1].nativeElement.click();
                        fixture.detectChanges();
                        tick();
                    }));

                    it('should have "All" checkbox is checked', () => {
                        expect(allCheckBox.nativeElement.checked).toBeTruthy();
                    });
                });
            });

            describe('uncheck "All"', () => {
                beforeEach(fakeAsync(() => {
                    allCheckBox.nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should uncheck "Item11" & its children', () => {
                    const itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                    const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                    expect(checkedValues).toEqual([false, false, false, false]);
                });

                describe('clear filter', () => {
                    beforeEach(fakeAsync(() => {
                        eventHelper.raiseInput(filterTextBox.nativeElement, '');
                        fixture.detectChanges();
                        tick();
                    }));

                    it('should have "Item12" & "Item2" are checked', () => {
                        const itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
                        const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                        expect(checkedValues).toEqual([false, false, false, false, true, true]);
                    });
                });
            });
        });

        describe('filtering "Item111"', () => {
            let itemCheckboxes: DebugElement[];

            beforeEach(fakeAsync(() => {
                eventHelper.raiseInput(filterTextBox.nativeElement, 'Item111');
                fixture.detectChanges();
                tick();
                itemCheckboxes = queryItemCheckboxes(fixture.debugElement);
            }));

            it('should display "Item1", "Item11" & "Item111"', () => {
                const texts = queryItemTexts(fixture.debugElement);
                expect(texts).toEqual(['Item1', 'Item11', 'Item111']);
            });

            describe('uncheck "Item1"', () => {
                beforeEach(fakeAsync(() => {
                    itemCheckboxes[0].nativeElement.click();
                    fixture.detectChanges();
                    tick();
                }));

                it('should have displayed items are unchecked', () => {
                    const checkedValues = itemCheckboxes.map((checkbox) => checkbox.nativeElement.checked);
                    expect(checkedValues).toEqual([false, false, false]);
                });
            });
        });

        describe('filtering "fake"', () => {
            beforeEach(fakeAsync(() => {
                eventHelper.raiseInput(filterTextBox.nativeElement, 'fake');
                fixture.detectChanges();
                tick();
            }));

            it('should display filter', () => {
                const filterInput = fixture.debugElement.query(By.css('input[type="text"]'));
                expect(filterInput).not.toBeNull();
            });

            it('should not display any items', () => {
                const texts = queryItemTexts(fixture.debugElement);
                expect(texts).toEqual([]);
            });

            it('should display a text "No items found"', () => {
                const textElement = fixture.debugElement.query(By.css('.treeview-text'));
                expect(textElement.nativeElement.textContent.trim()).toBe('No items found');
            });
        });
    });
});
