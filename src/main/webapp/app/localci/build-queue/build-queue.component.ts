import { Component, ViewChild } from '@angular/core';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

    isLoading = false;
    hasExamStarted = false;
    hasExamEnded = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    name?: string;
    participationId?: number;
    repositoryType?: string;
    commitHash?: string;
    submissionDate?: number;
    buildStartDate?: number;
    courseId?: number;
    priority?: number;

    isAdmin = false;

    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass;
    };
}
