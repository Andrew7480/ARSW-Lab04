# ARSW-Lab04

# Resumen


### 1. Familiarización con el código base
- Revisa el paquete `model` con las clases `Blueprint` y `Point`.
- Entiende la capa `persistence` con `InMemoryBlueprintPersistence`.  
- Analiza la capa `services` (`BlueprintsServices`) y el controlador `BlueprintsAPIController`.

**Capa `model`**

- `Point` es un *record* de Java con dos campos: `x` e `y`, que representan una coordenada en el plano 2D.
- `Blueprint` tiene tres atributos: `author` (String), `name` (String) y una lista interna de `Point`. Su identidad (equals/hashCode) está definida por la combinación `author + name`, lo que permite usarla como clave única. La lista de puntos se expone de forma inmutable (`getPoints()` devuelve `unmodifiableList`); para agregar puntos se usa `addPoint(Point p)`.

**Capa `persistence`**

- `BlueprintPersistence` es la **interfaz (puerto)** que define el contrato de acceso a datos: `saveBlueprint`, `getBlueprint`, `getBlueprintsByAuthor`, `getAllBlueprints` y `addPoint`.
- `InMemoryBlueprintPersistence` es el **adaptador** que implementa ese puerto usando un `ConcurrentHashMap<String, Blueprint>` (thread-safe). La clave del mapa es la cadena `author:name`. Al iniciar, carga tres blueprints de muestra (john/house, john/garage, jane/garden).
- `BlueprintNotFoundException` y `BlueprintPersistenceException` son excepciones chequeadas (*checked*) que señalan, respectivamente, que un blueprint no existe o que ocurrió un error al persistir (por ejemplo, intentar guardar un blueprint duplicado).

**Capa `services`**

- `BlueprintsServices` inyecta `BlueprintPersistence` y `BlueprintsFilter` como **interfaces**, no como implementaciones concretas. Esto aplica el **patrón Estrategia (Strategy)** combinado con el patrón **Puerto/Adaptador**: se puede cambiar la implementación de persistencia (de memoria a PostgreSQL) o el filtro activo sin modificar el servicio.
- El método `getBlueprint()` es el único que aplica el filtro antes de devolver el resultado; los demás métodos delegan directamente a la capa de persistencia.

**Capa `filters`**

- `BlueprintsFilter` es una interfaz con un único método `apply(Blueprint) → Blueprint`, lo que la hace compatible con expresiones lambda.
- `IdentityFilter` es la implementación por defecto: devuelve el blueprint sin ninguna modificación.

**Capa `controllers`**

- `BlueprintsAPIController` es un `@RestController` mapeado a `/blueprints` que expone cinco endpoints:

| Método | Path | Descripción |
|--------|------|-------------|
| GET | `/blueprints` | Retorna todos los blueprints |
| GET | `/blueprints/{author}` | Retorna todos los blueprints de un autor |
| GET | `/blueprints/{author}/{bpname}` | Retorna un blueprint específico (con filtro aplicado) |
| POST | `/blueprints` | Crea un nuevo blueprint |
| PUT | `/blueprints/{author}/{bpname}/points` | Agrega un punto a un blueprint existente |

El controlador utiliza `BlueprintsServices` como única dependencia, respetando la separación de capas.


### 2. Migración a persistencia en PostgreSQL
- Configura una base de datos PostgreSQL (puedes usar Docker).  
- Implementa un nuevo repositorio `PostgresBlueprintPersistence` que reemplace la versión en memoria.  
- Mantén el contrato de la interfaz `BlueprintPersistence`.  

**Decisiones de diseño**

Se mantiene el patrón **Puerto/Adaptador**: la interfaz `BlueprintPersistence` no cambió; solo se agregó una nueva implementación. El perfil de Spring (`@Profile("postgres")` / `@Profile("memory")`) decide en tiempo de arranque cuál adaptador inyectar, sin tocar nada más de la aplicación.

**Estructura agregada**

```
persistence/
  exception/
    BlueprintNotFoundException.java      ← excepciones movidas a subpaquete
    BlueprintPersistenceException.java
  memory/
    InMemoryBlueprintPersistence.java    ← @Profile("memory")
  postgresql/
    entity/
      BlueprintEntity.java               ← entidad JPA con UUID como PK
      PointEmbeddable.java               ← @Embeddable, puntos en tabla "points"
    mapper/
      BlueprintMapper.java               ← MapStruct: Blueprint ↔ BlueprintEntity
    repository/
      BlueprintJpaRepository.java        ← Spring Data JPA
    PostgresBlueprintPersistence.java    ← @Profile("postgres"), implementa el puerto
```

**Modelo relacional generado por Hibernate**

| Tabla | Columnas clave |
|-------|---------------|
| `blueprints` | `id` (UUID PK), `author`, `name` (UNIQUE juntos) |
| `points` | `blueprint_id` (FK → blueprints), `x`, `y` |

Los puntos se almacenan como `@ElementCollection` en la tabla `points`, ligados por `blueprint_id`.

**Configuración por perfiles**

`application.yml` activa el perfil `postgres` por defecto:
```yaml
spring:
  profiles:
    active: postgres
```

`application-postgres.yml` define la conexión:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/blueprints
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
```

Para levantar PostgreSQL con Docker:
```bash
docker run --name blueprints-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=blueprints -p 5432:5432 -d postgres:16
```

Para usar persistencia en memoria (sin base de datos):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=memory
mvn spring-boot:run "-Dspring-boot.run.profiles=memory"

```
