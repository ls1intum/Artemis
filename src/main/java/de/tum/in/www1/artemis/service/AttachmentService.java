package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.repository.AttachmentRepository;

@Service
@Transactional
public class AttachmentService {

    private AttachmentRepository attachmentRepository;

    public AttachmentService(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    public List<Attachment> findAllByLectureId(Long lectureId) {
        return attachmentRepository.findAllByLectureId(lectureId);
    }

}
