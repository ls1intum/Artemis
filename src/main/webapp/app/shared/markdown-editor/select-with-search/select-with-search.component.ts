import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MatMenuItem, MatMenuTrigger } from '@angular/material/menu';

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

    private searchTerm = '';
    private isSearching = false;
    private ngUnsubscribe = new Subject<void>();
    private readonly search$ = new Subject<string>();
    private focusInput = true;

    values: any[] = [];
    selectedValue: any;

    constructor(private readonly alertService: AlertService) {}

    ngOnInit(): void {
        this.command.setSelectWithSearchComponent(this);

        this.search$
            .pipe(
                debounceTime(300),
                distinctUntilChanged((prev, curr) => {
                    return prev === curr;
                }),
                tap((searchTerm) => {
                    this.isSearching = true;
                    this.searchTerm = searchTerm;
                }),
                switchMap(() => this.command.performSearch(this.searchTerm)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (res: HttpResponse<any[]>) => {
                    this.values = res.body!;
                    this.isSearching = false;
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.isSearching = false;
                    onError(this.alertService, errorResponse);
                },
            });
        this.search$.next(this.searchTerm);
    }

    open() {
        this.menuTrigger.openMenu();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    updateSearchTerm(searchInput: string | undefined) {
        const searchTerm = searchInput?.trim().toLowerCase() ?? '';
        this.searchTerm = searchTerm;
        this.search$.next(searchTerm);
    }

    handleNavigation(event: KeyboardEvent) {
        if (event.code === 'ArrowUp' || event.code === 'ArrowDown') {
            this.focusInput = false;
        }
    }

    focusInputField(force = false) {
        if (this.focusInput || force) {
            this.focusInput = true;
            this.searchInput.nativeElement.focus();
        }
    }

    protected readonly console = console;

    handleMenuOpen() {
        this.focusInput = true;
        this.searchInputForm.focus();
        this.searchInput.nativeElement.value = '';
        this.updateSearchTerm('');
    }

    handleMenuClosed() {
        this.focusInput = false;
        this.command.insertSelection(this.selectedValue);
        this.selectedValue = undefined;
    }

    setSelection(value: any) {
        this.selectedValue = value;
    }
}
