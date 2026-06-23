package edu.eci.arsw.blueprints.persistence.postgresql.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blueprints", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "author", "name" })
})
@Data
@NoArgsConstructor
public class BlueprintEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(nullable = false)
    private String author;
    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "points", joinColumns = @JoinColumn(name = "blueprint_id"))
    private List<PointEmbeddable> points = new ArrayList<>();
}
