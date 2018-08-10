import {Component, Input, OnInit} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {RepositoryFileService} from '../../entities/repository';
import {Participation} from '../../entities/participation';
import {EditorFileBrowserComponent} from './editor-file-browser.component';

// Modal -> Create new repository file
@Component({
    selector: 'jhi-editor-file-browser-create',
    templateUrl: './create-file.html'
})
export class EditorFileBrowserCreateComponent implements OnInit {
    @Input() participation: Participation;
    @Input() parent: EditorFileBrowserComponent;

    isLoading: boolean;
    newFileFolder: string;
    newFileName: string;

    constructor(public activeModal: NgbActiveModal,
                private repositoryFileService: RepositoryFileService) {}

    ngOnInit(): void {
        this.isLoading = false;
    }

    createFile() {
        this.isLoading = true;
        // Make sure we have a filename
        if (this.newFileName) {
            const absoluteFilePath = (this.newFileFolder ? this.newFileFolder + '/' : '') + this.newFileName;
            this.repositoryFileService.create(this.participation.id, absoluteFilePath).subscribe( res => {
                this.isLoading = false;
                console.log('Successfully created file: ' + absoluteFilePath, res);
                this.closeModal();
                // TODO: select newly created file
                this.parent.onCreatedFile({file: this.newFileName});
            }, err => {
                console.log('Error while creating file: ' + this.newFileName, err);
            });
        }
        this.isLoading = false;
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
