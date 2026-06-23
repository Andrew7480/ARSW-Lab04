package edu.eci.arsw.blueprints.persistence.postgresql.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointEmbeddable {

    private int x;

    private int y;
}