package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

class HyperionCourseSettingsServiceTest {

    @Mock
    private HyperionCourseSettingsRepository settingsRepository;

    @Mock
    private CourseTestRepository courseRepository;

    private HyperionCourseSettingsService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionCourseSettingsService(settingsRepository, courseRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void getSettings_returnsDefaultsWhenTheCourseHasNoRow() {
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        HyperionCourseSettings settings = service.getSettings(5L);

        assertThat(settings).isEqualTo(new HyperionCourseSettings(false, 12, false, 12, 20, 50, 30));
    }

    @Test
    void getSettings_returnsTheStoredPayload() {
        HyperionCourseSettingsEntity entity = new HyperionCourseSettingsEntity();
        entity.setCourseId(5L);
        entity.setSettings(new HyperionCourseSettings(true, 30, true, 10, 25, 50, 25));
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.of(entity));

        assertThat(service.getSettings(5L)).isEqualTo(new HyperionCourseSettings(true, 30, true, 10, 25, 50, 25));
    }

    @Test
    void updateSettings_replacesTheRowInPlace() {
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 8, false, 12, 20, 50, 30);
        when(settingsRepository.updateSettings(5L, settings)).thenReturn(1);

        HyperionCourseSettings stored = service.updateSettings(5L, settings);

        assertThat(stored).isEqualTo(settings);
        verify(settingsRepository).updateSettings(5L, settings);
        verify(courseRepository, never()).ensureDefaultConfigurations(anyLong());
    }

    @Test
    void updateSettings_addsTheMissingRowBeforeWriting() {
        HyperionCourseSettings settings = new HyperionCourseSettings(true, 12, false, 12, 20, 50, 30);
        when(settingsRepository.updateSettings(5L, settings)).thenReturn(0, 1);

        service.updateSettings(5L, settings);

        InOrder order = inOrder(settingsRepository, courseRepository);
        order.verify(settingsRepository).updateSettings(5L, settings);
        order.verify(courseRepository).ensureDefaultConfigurations(5L);
        order.verify(settingsRepository).updateSettings(5L, settings);
    }

    @Test
    void settings_substituteDefaultsForFieldsAnOlderPayloadLacks() {
        HyperionCourseSettings settings = new HyperionCourseSettings(null, null, null, null, null, null, null, null);

        assertThat(settings).isEqualTo(HyperionCourseSettings.defaultSettings());
    }

    @Test
    void settings_readAPayloadWithOneCountAsTheSingleChoiceCount() {
        HyperionCourseSettings settings = new HyperionCourseSettings(true, null, 25, null, null, 10, null, null);

        assertThat(settings).isEqualTo(new HyperionCourseSettings(true, 25, false, 12, 20, 50, 30));
    }

    @Test
    void settings_preferTheSingleChoiceCountOverTheOlderCount() {
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 7, 25, true, 3, 0, 100, 0);

        assertThat(settings).isEqualTo(new HyperionCourseSettings(false, 7, true, 3, 0, 100, 0));
    }
}
