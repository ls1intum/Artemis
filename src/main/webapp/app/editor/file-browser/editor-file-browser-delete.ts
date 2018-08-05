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

    isLoading: boolean;

    constructor(public activeModal: NgbActiveModal,
                private repositoryFileService: RepositoryFileService) {}

    ngOnInit(): void {
        this.isLoading = false;
    }

    deleteFile() {
        this.isLoading = true;
        // Make sure we have a filename
        if (this.fileNameToDelete) {
            this.repositoryFileService.delete(this.participation.id, this.fileNameToDelete).subscribe( res => {
                console.log('Successfully deleted file: ' + this.fileNameToDelete, res);
                this.closeModal();
                this.parent.getRepositoryFiles();
                this.parent.onDeletedFile({file: this.fileNameToDelete});
            }, err => {
                console.log('Error deleting file: ' + this.fileNameToDelete, err);
            });
        }
        this.isLoading = false;
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
