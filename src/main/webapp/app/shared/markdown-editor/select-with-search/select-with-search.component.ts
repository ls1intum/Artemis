import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, debounce, distinctUntilChanged, switchMap, takeUntil, timer } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MatMenuItem, MatMenuTrigger } from '@angular/material/menu';

interface SearchQuery {
    searchTerm: string;
    noDebounce: boolean;
}

@Component({
    selector: 'jhi-select-with-search',
    templateUrl: './select-with-search.component.html',
    styleUrls: ['./select-with-search.component.scss'],
})
export class SelectWithSearchComponent implements OnInit, OnDestroy {
    @Input() command: InteractiveSearchCommand;
    @Output() valueSelect = new EventEmitter<any | undefined>();

    @ViewChild(MatMenuTrigger) menuTrigger: MatMenuTrigger;
    @ViewChild('searchInputForm') searchInputForm: MatMenuItem;
    @ViewChild('searchInput') searchInput: ElementRef;

    private ngUnsubscribe = new Subject<void>();
    private readonly search$ = new Subject<SearchQuery>();

    values: any[] = [];
    selectedValue: any;
    focusInput = true;

    constructor(private readonly alertService: AlertService) {}

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
                next: (res: HttpResponse<any[]>) => {
                    this.values = res.body!;
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                },
            });
    }

    open() {
        this.menuTrigger.openMenu();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    updateSearchTerm(searchInput: string | undefined, noDebounce = false) {
        const searchTerm = searchInput?.trim().toLowerCase() ?? '';
        this.search$.next({ searchTerm, noDebounce });
    }

    focusInputField(force = false) {
        if (this.focusInput || force) {
            this.focusInput = true;
            this.searchInput.nativeElement.focus();
        }
    }

    handleMenuOpen() {
        this.focusInput = true;
        this.searchInputForm.focus();
        this.searchInput.nativeElement.value = '';
        this.updateSearchTerm('', true);
    }

    handleMenuClosed() {
        this.focusInput = false;
        this.command.insertSelection(this.selectedValue);
        this.selectedValue = undefined;
    }

    setSelection(value: any) {
        this.selectedValue = value;
    }

    fillSelection() {
        if (this.values?.length > 0) {
            this.setSelection(this.values.last());
            this.menuTrigger.closeMenu();
        }
    }
}
