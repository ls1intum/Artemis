import { ChangeDetectorRef, Component, ElementRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { InteractiveSearchCommand, SelectableItem } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, debounce, distinctUntilChanged, switchMap, takeUntil, timer } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { NgbDropdown, NgbDropdownConfig } from '@ng-bootstrap/ng-bootstrap';

interface SearchQuery {
    searchTerm: string;
    noDebounce: boolean;
}

@Component({
    selector: 'jhi-select-with-search',
    templateUrl: './select-with-search.component.html',
    styleUrls: ['./select-with-search.component.scss'],
    providers: [NgbDropdownConfig],
})
export class SelectWithSearchComponent implements OnInit, OnChanges, OnDestroy {
    @Input() command: InteractiveSearchCommand;
    @Input() editorContentString: string;

    @ViewChild(NgbDropdown) dropdown: NgbDropdown;
    @ViewChild('dropdown') dropdownRef: ElementRef;

    private ngUnsubscribe = new Subject<void>();
    private readonly search$ = new Subject<SearchQuery>();

    values: SelectableItem[] = [];
    selectedValue: SelectableItem | undefined;
    offsetX: string;
    offsetY: string;

    constructor(
        private readonly alertService: AlertService,
        private readonly cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.command.setSelectWithSearchComponent(this);

        this.search$
            .pipe(
                debounce((searchQuery) => {
                    return timer(searchQuery.noDebounce ? 0 : 200);
                }),
                distinctUntilChanged((prev, curr) => {
                    return prev === curr;
                }),
                switchMap((searchQuery) => this.command.performSearch(searchQuery.searchTerm)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (res: HttpResponse<SelectableItem[]>) => {
                    this.values = res.body!;
                    this.cdr.detectChanges();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                },
            });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.editorContentString) {
            this.command.updateSearchTerm();
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    open() {
        this.dropdown.open();
    }

    close() {
        this.dropdown.close();
    }

    updateSearchTerm(searchInput: string | undefined, noDebounce = false) {
        const searchTerm = searchInput?.trim().toLowerCase() ?? '';
        this.search$.next({ searchTerm, noDebounce });
    }

    handleMenuOpen() {
        const cursorPosition = this.command.getCursorScreenPosition();
        const dropdownPosition = this.dropdownRef.nativeElement.getBoundingClientRect();
        this.offsetX = cursorPosition.pageX - dropdownPosition.left + 'px';
        this.offsetY = cursorPosition.pageY - dropdownPosition.top + 'px';
        this.updateSearchTerm('', true);
        this.cdr.detectChanges();
    }

    handleMenuClosed() {
        this.command.insertSelection(this.selectedValue);
        this.selectedValue = undefined;
    }

    setSelection(value: any) {
        this.selectedValue = value;
        this.dropdown.close();
    }

    handleToggle() {
        if (this.dropdown.isOpen()) {
            this.close();
        } else {
            this.command.execute();
        }
    }
}
