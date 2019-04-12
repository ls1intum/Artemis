import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture';
import { Attachment, AttachmentService, AttachmentType } from 'app/entities/attachment';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';

@Component({
    selector: 'jhi-lecture-attachments',
    templateUrl: './lecture-attachments.component.html',
})
export class LectureAttachmentsComponent implements OnInit {
    lecture: Lecture;
    attachments: Attachment[] = [];
    attachmentToBeCreated: Attachment;
    attachmentFile: any;
    isUploadingAttachment: boolean;

    constructor(protected activatedRoute: ActivatedRoute, private attachmentService: AttachmentService, private fileUploaderService: FileUploaderService) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.attachmentService.findAllByLectureId(this.lecture.id).subscribe((attachmentsResponse: HttpResponse<Attachment[]>) => {
                this.attachments = attachmentsResponse.body;
            });
        });
    }

    previousState() {
        window.history.back();
    }

    addAttachment() {
        const newAttachment = new Attachment();
        newAttachment.lecture = this.lecture;
        newAttachment.attachmentType = AttachmentType.FILE;
        this.attachmentToBeCreated = newAttachment;
    }

    saveAttachment() {
        this.attachmentService.create(this.attachmentToBeCreated).subscribe((attachmentRes: HttpResponse<Attachment>) => {
            this.attachments.push(attachmentRes.body);
            this.attachmentToBeCreated = null;
        });
    }

    /**
     * @function setLectureAttachment
     * @param $event {object} Event object which contains the uploaded file
     */
    setLectureAttachment($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            const attachmentFile = fileList[0];
            this.attachmentFile = attachmentFile;
            this.attachmentToBeCreated.link = attachmentFile['name'];
        }
    }

    /**
     * @function uploadLectureAttachmentAndSave
     * @desc Upload the selected file and add it to the attachment
     */
    uploadLectureAttachmentAndSave(): void {
        const file = this.attachmentFile;

        this.isUploadingAttachment = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.attachmentToBeCreated.link = result.path;
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
                this.saveAttachment();
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingAttachment = false;
                this.attachmentFile = null;
            },
        );
    }
}
