package edu.eci.arsw.blueprints.persistence.postgresql.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.eci.arsw.blueprints.persistence.postgresql.entity.BlueprintEntity;

public interface BlueprintJpaRepository extends JpaRepository<BlueprintEntity, UUID> {

    Optional<BlueprintEntity> findByAuthorAndName(String author, String name);

    List<BlueprintEntity> findByAuthor(String author);
}
