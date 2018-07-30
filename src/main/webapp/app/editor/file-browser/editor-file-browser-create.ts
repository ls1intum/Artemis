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

    bIsLoading: boolean;
    newFileFolder: string;
    newFileName: string;

    constructor(public activeModal: NgbActiveModal,
                private repositoryFileService: RepositoryFileService) {}

    ngOnInit(): void {
        this.bIsLoading = false;
    }

    createFile() {
        this.bIsLoading = true;
        // Make sure we have a filename
        if (this.newFileName) {
            // TODO: check if we need to add prefix
            const extendedNewFileName = this.newFileName;
            this.repositoryFileService.create(this.participation.id, extendedNewFileName).subscribe( res => {
                this.bIsLoading = false;
                console.log('Successfully created file: ' + extendedNewFileName, res);
                this.closeModal();
                // TODO: maybe emit onFileCreated and let parent handle the rest? folder?
                this.parent.getRepositoryFiles();
                // TODO: select newly created file
                this.parent.onCreatedFile({file: extendedNewFileName});
            }, err => {
                console.log('Error while creating file: ' + extendedNewFileName, err);
            });
        }
        this.bIsLoading = false;
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
