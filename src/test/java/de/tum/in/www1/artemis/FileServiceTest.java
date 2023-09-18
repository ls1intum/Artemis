package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.FileService;

class FileServiceTest {

    private static final String TEST_PREFIX = "fileService";

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Ignore_Path_Null() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(null, URI.create("/api/" + FileService.DRAG_AND_DROP_BACKGROUND_SUBPATH + "/")))
                .doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Ignore_SubPath_Null() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/backgrounds/1/../../BackgroundFile.jpg"), null))
                .doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Ignore_Path_Temp() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/temp/backgrounds/1/../../BackgroundFile.jpg"),
                URI.create("/api/" + FileService.DRAG_AND_DROP_BACKGROUND_SUBPATH + "/"))).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Ignore_SubPath_Temp() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/backgrounds/1/../../BackgroundFile.jpg"),
                URI.create("/api/files/temp/"))).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Valid() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/backgrounds/1/BackgroundFile.jpg"),
                URI.create("/api/" + FileService.DRAG_AND_DROP_BACKGROUND_SUBPATH + "/"))).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Invalid() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/backgrounds/1/../../BackgroundFile.jpg"),
                        URI.create("/api/" + FileService.DRAG_AND_DROP_BACKGROUND_SUBPATH + "/")));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Picture_Valid() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/drag-items/1/PictureFile.jpg"),
                URI.create("/api/" + FileService.DRAG_AND_DROP_PICTURE_SUBPATH + "/"))).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Picture_Invalid() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow(URI.create("/api/files/drag-and-drop/drag-items/1/../../PictureFile.jpg"),
                        URI.create("/api/" + FileService.DRAG_AND_DROP_PICTURE_SUBPATH + "/")));
    }
}
