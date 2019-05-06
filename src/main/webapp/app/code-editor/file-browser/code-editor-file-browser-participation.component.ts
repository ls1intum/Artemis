import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Observable } from 'rxjs';

import { CodeEditorFileBrowserComponent } from './code-editor-file-browser.component';
import { RepositoryFileService } from 'app/entities/repository';
import { WindowRef } from 'app/core';
import { CodeEditorComponent } from '../code-editor.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { Participation, hasParticipationChanged } from 'app/entities/participation';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { CodeEditorParticipationComponent } from '../code-editor-participation.component';

@Component({
    selector: 'jhi-code-editor-file-browser-participation',
    templateUrl: './code-editor-file-browser.component.html',
})
export class CodeEditorFileBrowserParticipationComponent extends CodeEditorFileBrowserComponent implements OnChanges {
    @Input()
    participation: Participation;

    constructor(parent: CodeEditorParticipationComponent, $window: WindowRef, modalService: NgbModal, private repositoryFileService: RepositoryFileService) {
        super(parent, $window, modalService);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.isInitial = true;
        }
        super.ngOnChanges(changes);
    }
    /**
     * Load files from the participants repository.
     * Files that are not relevant for the conduction of the exercise are removed from result.
     */
    loadFiles = (): Observable<{ [fileName: string]: FileType }> => {
        this.isLoadingFiles = true;
        return this.repositoryFileService.query(this.participation.id);
    };

    renameFile = (filePath: string, fileName: string): Observable<void> => {
        return this.repositoryFileService.rename(this.participation.id, filePath, fileName);
    };

    createFile = (fileName: string): Observable<void> => {
        return this.repositoryFileService.createFile(this.participation.id, fileName);
    };

    createFolder = (folderName: string): Observable<void> => {
        return this.repositoryFileService.createFolder(this.participation.id, folderName);
    };
}
