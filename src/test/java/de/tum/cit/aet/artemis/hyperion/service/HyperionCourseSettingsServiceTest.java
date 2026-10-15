package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

class HyperionCourseSettingsServiceTest {

    @Mock
    private HyperionCourseSettingsRepository settingsRepository;

    private HyperionCourseSettingsService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionCourseSettingsService(settingsRepository);
        when(settingsRepository.save(any(HyperionCourseSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void getSettings_returnsDefaultsWhenTheCourseHasNoRow() {
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        HyperionCourseSettings settings = service.getSettings(5L);

        assertThat(settings.poolGenerationEnabled()).isFalse();
        assertThat(settings.questionsPerLectureUnit()).isEqualTo(HyperionCourseSettings.DEFAULT_QUESTIONS_PER_LECTURE_UNIT);
    }

    @Test
    void getSettings_returnsTheStoredPayload() {
        HyperionCourseSettingsEntity entity = new HyperionCourseSettingsEntity();
        entity.setCourseId(5L);
        entity.setSettings(new HyperionCourseSettings(true, 30));
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.of(entity));

        assertThat(service.getSettings(5L)).isEqualTo(new HyperionCourseSettings(true, 30));
    }

    @Test
    void updateSettings_createsTheRowOnFirstSave() {
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        HyperionCourseSettings stored = service.updateSettings(5L, new HyperionCourseSettings(true, 12));

        ArgumentCaptor<HyperionCourseSettingsEntity> saved = ArgumentCaptor.forClass(HyperionCourseSettingsEntity.class);
        verify(settingsRepository).save(saved.capture());
        assertThat(saved.getValue().getCourseId()).isEqualTo(5L);
        assertThat(saved.getValue().getSettings()).isEqualTo(new HyperionCourseSettings(true, 12));
        assertThat(stored).isEqualTo(new HyperionCourseSettings(true, 12));
    }

    @Test
    void updateSettings_replacesTheExistingPayload() {
        HyperionCourseSettingsEntity entity = new HyperionCourseSettingsEntity();
        entity.setCourseId(5L);
        entity.setSettings(new HyperionCourseSettings(true, 30));
        when(settingsRepository.findByCourseId(5L)).thenReturn(Optional.of(entity));

        service.updateSettings(5L, new HyperionCourseSettings(false, 8));

        verify(settingsRepository).save(entity);
        assertThat(entity.getSettings()).isEqualTo(new HyperionCourseSettings(false, 8));
    }

    @Test
    void settings_substituteDefaultsForFieldsAnOlderPayloadLacks() {
        HyperionCourseSettings settings = new HyperionCourseSettings((Boolean) null, (Integer) null);

        assertThat(settings.poolGenerationEnabled()).isFalse();
        assertThat(settings.questionsPerLectureUnit()).isEqualTo(HyperionCourseSettings.DEFAULT_QUESTIONS_PER_LECTURE_UNIT);
    }
}
