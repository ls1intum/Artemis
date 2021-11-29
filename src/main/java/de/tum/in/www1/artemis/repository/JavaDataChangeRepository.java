package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.JavaDataChangelog;

public interface JavaDataChangeRepository extends JpaRepository<JavaDataChangelog, String> {
}
