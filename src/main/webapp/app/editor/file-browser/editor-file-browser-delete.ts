import {Component, Input, OnInit} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {RepositoryFileService} from '../../entities/repository';
import {Participation} from '../../entities/participation';
import {EditorFileBrowserComponent} from './editor-file-browser.component';

// Modal -> Delete repository file
@Component({
    selector: 'jhi-editor-file-browser-delete',
    templateUrl: './delete-file.html'
})
export class EditorFileBrowserDeleteComponent implements OnInit {
    @Input() participation: Participation;
    @Input() fileNameToDelete: string;
    @Input() parent: EditorFileBrowserComponent;

    bIsLoading: boolean;

    constructor(public activeModal: NgbActiveModal,
                private repositoryFileService: RepositoryFileService) {}

    ngOnInit(): void {
        this.bIsLoading = false;
        console.log('fileNameToDelete: ' + this.fileNameToDelete);
    }

    deleteFile() {
        this.bIsLoading = true;
        // Make sure we have a filename
        if (this.fileNameToDelete) {

            // TODO: check if we need to add prefix
            const extendedNewFileName = this.fileNameToDelete;
            this.repositoryFileService.delete(this.participation.id, extendedNewFileName).subscribe( res => {
                console.log('Successfully deleted file: ' + extendedNewFileName, res);
                this.closeModal();
                this.parent.getRepositoryFiles();
                this.parent.onDeletedFile({file: extendedNewFileName});
            }, err => {
                console.log('Error deleting file: ' + extendedNewFileName, err);
            });
        }
        this.bIsLoading = false;
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
