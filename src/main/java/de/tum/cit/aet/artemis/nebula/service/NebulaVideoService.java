package de.tum.cit.aet.artemis.nebula.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.domain.VideoLecture;
import de.tum.cit.aet.artemis.nebula.dto.LectureVideoDTO;
import de.tum.cit.aet.artemis.nebula.dto.VideoUploadResponseDTO;
import de.tum.cit.aet.artemis.nebula.repository.VideoLectureRepository;

/**
 * Service for managing lecture videos through Nebula Video Storage.
 * Orchestrates the upload, deletion, and retrieval of lecture videos
 * by coordinating between VideoLecture entity and VideoStorageService.
 */
@Conditional(NebulaEnabled.class)
@Service
@Lazy
@Profile(PROFILE_CORE)
public class NebulaVideoService {

    private static final Logger log = LoggerFactory.getLogger(NebulaVideoService.class);

    private final VideoLectureRepository videoLectureRepository;

    private final VideoStorageService videoStorageService;

    public NebulaVideoService(VideoLectureRepository videoLectureRepository, VideoStorageService videoStorageService) {
        this.videoLectureRepository = videoLectureRepository;
        this.videoStorageService = videoStorageService;
    }

    /**
     * Uploads a video for a lecture and stores the metadata.
     *
     * @param lecture the lecture to associate the video with
     * @param file    the video file to upload
     * @return LectureVideoDTO containing the video metadata
     * @throws ResponseStatusException if upload fails or lecture already has a video
     */
    public LectureVideoDTO uploadLectureVideo(Lecture lecture, MultipartFile file) {
        log.info("Uploading video for lecture ID: {}", lecture.getId());

        // Check if lecture already has a video - if so, delete it first
        Optional<VideoLecture> existingVideo = videoLectureRepository.findByLectureId(lecture.getId());
        if (existingVideo.isPresent()) {
            log.info("Lecture {} already has a video. Deleting existing video before uploading new one.", lecture.getId());
            try {
                deleteLectureVideo(lecture.getId());
            }
            catch (Exception e) {
                log.warn("Failed to delete existing video, but continuing with upload: {}", e.getMessage());
            }
        }

        // Upload to Nebula Video Storage Service
        VideoUploadResponseDTO uploadResponse = videoStorageService.uploadVideo(file);

        // Create VideoLecture entity
        VideoLecture videoLecture = new VideoLecture();
        videoLecture.setLecture(lecture);
        videoLecture.setVideoId(uploadResponse.videoId());
        videoLecture.setFilename(uploadResponse.filename());
        videoLecture.setPlaylistUrl(uploadResponse.playlistUrl());
        videoLecture.setDurationSeconds(uploadResponse.durationSeconds());
        // Convert LocalDateTime from Nebula to ZonedDateTime with system default timezone
        videoLecture.setUploadedAt(uploadResponse.uploadedAt().atZone(ZoneId.systemDefault()));
        videoLecture.setSizeBytes(uploadResponse.sizeBytes());

        videoLecture = videoLectureRepository.save(videoLecture);

        log.info("Video uploaded successfully for lecture ID: {}, video ID: {}", lecture.getId(), videoLecture.getVideoId());

        return convertToDTO(videoLecture);
    }

    /**
     * Deletes a lecture video and its metadata.
     *
     * @param lectureId the ID of the lecture whose video should be deleted
     * @throws ResponseStatusException if video not found or deletion fails
     */
    public void deleteLectureVideo(Long lectureId) {
        log.info("Deleting video for lecture ID: {}", lectureId);

        VideoLecture videoLecture = videoLectureRepository.findByLectureId(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No video found for this lecture"));

        // Delete from Nebula Video Storage Service
        videoStorageService.deleteVideo(videoLecture.getVideoId());

        // Delete from database
        videoLectureRepository.delete(videoLecture);

        log.info("Video deleted successfully for lecture ID: {}", lectureId);
    }

    /**
     * Gets the video metadata for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return Optional containing the video metadata if found
     */
    public Optional<LectureVideoDTO> getLectureVideo(Long lectureId) {
        return videoLectureRepository.findByLectureId(lectureId).map(this::convertToDTO);
    }

    /**
     * Gets the HLS streaming URL for a lecture video.
     *
     * @param lectureId the ID of the lecture
     * @return the streaming URL
     * @throws ResponseStatusException if video not found
     */
    public String getVideoStreamingUrl(Long lectureId) {
        VideoLecture videoLecture = videoLectureRepository.findByLectureId(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No video found for this lecture"));

        return videoStorageService.getVideoStreamingUrl(videoLecture.getVideoId());
    }

    /**
     * Checks if a lecture has an associated video.
     *
     * @param lectureId the ID of the lecture
     * @return true if the lecture has a video, false otherwise
     */
    public boolean hasVideo(Long lectureId) {
        return videoLectureRepository.existsByLectureId(lectureId);
    }

    /**
     * Converts a VideoLecture entity to a DTO.
     *
     * @param videoLecture the entity to convert
     * @return the DTO
     */
    private LectureVideoDTO convertToDTO(VideoLecture videoLecture) {
        return new LectureVideoDTO(videoLecture.getLecture().getId(), videoLecture.getVideoId(), videoLecture.getFilename(), videoLecture.getSizeBytes(),
                videoLecture.getUploadedAt(), videoLecture.getPlaylistUrl(), videoLecture.getDurationSeconds());
    }
}
