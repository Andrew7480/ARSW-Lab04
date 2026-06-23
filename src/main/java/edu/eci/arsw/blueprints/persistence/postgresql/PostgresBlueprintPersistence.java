package edu.eci.arsw.blueprints.persistence.postgresql;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.persistence.BlueprintPersistence;
import edu.eci.arsw.blueprints.persistence.exception.BlueprintNotFoundException;
import edu.eci.arsw.blueprints.persistence.exception.BlueprintPersistenceException;
import edu.eci.arsw.blueprints.persistence.postgresql.entity.BlueprintEntity;
import edu.eci.arsw.blueprints.persistence.postgresql.entity.PointEmbeddable;
import edu.eci.arsw.blueprints.persistence.postgresql.mapper.BlueprintMapper;
import edu.eci.arsw.blueprints.persistence.postgresql.repository.BlueprintJpaRepository;
import lombok.RequiredArgsConstructor;

@Repository
@Profile("postgres")
@RequiredArgsConstructor
public class PostgresBlueprintPersistence implements BlueprintPersistence {
    private final BlueprintJpaRepository repository;
    private final BlueprintMapper mapper;

    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        try {
            repository.save(mapper.toEntity(bp));
        } catch (Exception e) {
            throw new BlueprintPersistenceException("Error saving blueprint");
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        return repository.findByAuthorAndName(author, name)
                .map(mapper::toDomain)
                .orElseThrow(() -> new BlueprintNotFoundException(author + ":" + name));
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {

        List<BlueprintEntity> entities = repository.findByAuthor(author);

        if (entities.isEmpty()) {
            throw new BlueprintNotFoundException("Author not found: " + author);
        }

        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {

        return repository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {

        BlueprintEntity entity = repository.findByAuthorAndName(author, name)
                .orElseThrow(() -> new BlueprintNotFoundException(author + ":" + name));

        entity.getPoints().add(new PointEmbeddable(x, y));

        repository.save(entity);
    }
}
