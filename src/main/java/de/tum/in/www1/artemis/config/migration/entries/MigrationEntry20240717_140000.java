package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.ParticipationVCSAccessTokenService;

public class MigrationEntry20240717_140000 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20240717_140000.class);

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationVCSAccessTokenService participationVCSAccessTokenService;

    public MigrationEntry20240717_140000(ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ParticipationVCSAccessTokenService participationVCSAccessTokenService) {
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.participationVCSAccessTokenService = participationVCSAccessTokenService;
    }

    @Override
    public void execute() {
        List<ProgrammingExerciseStudentParticipation> programmingExerciseStudentParticipations = programmingExerciseStudentParticipationRepository.findAll();
        participationVCSAccessTokenService.createMissingParticipationVCSAccessTokens(programmingExerciseStudentParticipations);
        log.info("Creating missing participation VCS access tokens");
    }

    @Override
    public String author() {
        return "entholzer";
    }

    @Override
    public String date() {
        return "20240717_140000";
    }
}
