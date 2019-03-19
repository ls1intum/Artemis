import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryFileService } from 'app/entities/repository';
import { Participation } from 'app/entities/participation';
import { CodeEditorFileBrowserComponent } from 'app/code-editor';
import { TranslateService } from '@ngx-translate/core';

// Modal -> Create new repository file
@Component({
    selector: 'jhi-code-editor-file-browser-create',
    templateUrl: './code-editor-file-browser-create.html'
})
export class CodeEditorFileBrowserCreateComponent implements OnInit {
    @Input() participation: Participation;
    @Input() parent: CodeEditorFileBrowserComponent;
    @Input() folder: string;

    isLoading: boolean;
    newFileFolder: string;
    newFileName: string;
    // Placeholder string for form field 'folder'
    folderPlaceholder: string;

    constructor(
        public activeModal: NgbActiveModal,
        private repositoryFileService: RepositoryFileService,
        private translateService: TranslateService
    ) {}

    /**
     * @function ngOnInit
     * @desc Initializes variables
     */
    ngOnInit(): void {
        this.isLoading = false;
        this.folderPlaceholder = this.translateService.instant('arTeMiSApp.editor.fileBrowser.folderPlaceholder');
        /** Set folder if we received a value for it via input **/
        if (this.folder) {
            this.newFileFolder = this.folder;
        }
    }

    /**
     * @function createFile
     * @desc Reads the provided fileName and folder and creates a new file in the repository
     */
    createFile() {
        this.isLoading = true;
        // Make sure we have a filename
        if (this.newFileName) {
            const absoluteFilePath = (this.newFileFolder ? this.newFileFolder + '/' : '') + this.newFileName;
            this.repositoryFileService.create(this.participation.id, absoluteFilePath).subscribe(
                () => {
                    this.isLoading = false;
                    this.closeModal();
                    this.parent.onCreatedFile({ file: absoluteFilePath, mode: 'create' });
                },
                err => {
                    console.log('Error while creating file: ' + this.newFileName, err);
                }
            );
        }
        this.isLoading = false;
    }

    /**
     * @function closeModal
     * @desc Dismisses the currently active modal (popup)
     */
    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
