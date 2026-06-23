package edu.eci.arsw.blueprints.persistence.postgresql.mapper;

import org.mapstruct.Mapper;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;
import edu.eci.arsw.blueprints.persistence.postgresql.entity.BlueprintEntity;
import edu.eci.arsw.blueprints.persistence.postgresql.entity.PointEmbeddable;

@Mapper(componentModel = "spring")
public interface BlueprintMapper {

    Blueprint toDomain(BlueprintEntity entity);

    BlueprintEntity toEntity(Blueprint blueprint);

    Point toDomain(PointEmbeddable point);

    PointEmbeddable toEntity(Point point);
}