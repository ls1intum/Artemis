package de.tum.cit.aet.artemis.lecture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.service.TumLivePlaylistLookup;

class AttachmentVideoUnitPlaylistIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "attachmentvideounitplaylist";

    private static final String OTHER_PREFIX = TEST_PREFIX + "other";

    private static final String WATCH_PAGE = "https://live.rbg.tum.de/w/algo/42";

    private static final String PLAYLIST = "https://edge.live.rbg.tum.de/vod/42/COMB/playlist.m3u8?jwt=signed";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    private AttachmentVideoUnit unit;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        // Created after the course, so the enrollment above does not include it
        userUtilService.createAndSaveUser(OTHER_PREFIX + "student42");
        unit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        unit.setVideoSource(WATCH_PAGE);
        unit = attachmentVideoUnitRepository.save(unit);
        when(tumLiveService.lookUpPlaylist(WATCH_PAGE)).thenReturn(new TumLivePlaylistLookup(PLAYLIST, TumLivePlaylistLookup.Outcome.RESOLVED));
    }

    private String playlistPath() {
        return "/api/lecture/lecture-units/" + unit.getId() + "/playlist";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentGetsThePlaylistOfAReleasedUnit() throws Exception {
        mockMvc.perform(get(playlistPath())).andExpect(status().isOk()).andExpect(content().string(PLAYLIST));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentDoesNotGetThePlaylistOfAnUnreleasedUnit() throws Exception {
        unit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        attachmentVideoUnitRepository.save(unit);

        mockMvc.perform(get(playlistPath())).andExpect(status().isForbidden());
        verify(tumLiveService, never()).lookUpPlaylist(anyString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void tutorGetsThePlaylistOfAnUnreleasedUnit() throws Exception {
        unit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        attachmentVideoUnitRepository.save(unit);

        mockMvc.perform(get(playlistPath())).andExpect(status().isOk()).andExpect(content().string(PLAYLIST));
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student42", roles = "USER")
    void userOutsideTheCourseIsForbidden() throws Exception {
        mockMvc.perform(get(playlistPath())).andExpect(status().isForbidden());
        verify(tumLiveService, never()).lookUpPlaylist(anyString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unitWithoutAPlayableTumLiveVideoIsNotFound() throws Exception {
        when(tumLiveService.lookUpPlaylist(WATCH_PAGE)).thenReturn(new TumLivePlaylistLookup(null, TumLivePlaylistLookup.Outcome.NOT_ACCESSIBLE));
        mockMvc.perform(get(playlistPath())).andExpect(status().isNotFound());

        unit.setVideoSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        attachmentVideoUnitRepository.save(unit);
        mockMvc.perform(get(playlistPath())).andExpect(status().isNotFound());
    }
}
