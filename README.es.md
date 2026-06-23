# ARSW-Lab04

# Resumen

Este laboratorio construye una API REST de gestión de blueprints (planos de dibujo) usando Java 21 y Spring Boot 3.3.x. Se aplican progresivamente buenas prácticas de arquitectura: separación por capas, patrones Puerto/Adaptador y Estrategia, persistencia intercambiable (memoria / PostgreSQL), respuestas uniformes con `ApiResponse<T>`, documentación OpenAPI y filtros de procesamiento activables por perfiles de Spring.

---

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

- `BlueprintsAPIController` es un `@RestController` mapeado a `/api/v1/blueprints` que expone cinco endpoints:

| Método | Path | Código exitoso | Descripción |
|--------|------|---------------|-------------|
| GET | `/api/v1/blueprints` | 200 | Retorna todos los blueprints |
| GET | `/api/v1/blueprints/{author}` | 200 | Retorna todos los blueprints de un autor |
| GET | `/api/v1/blueprints/{author}/{bpname}` | 200 | Retorna un blueprint específico (con filtro aplicado) |
| POST | `/api/v1/blueprints` | 201 | Crea un nuevo blueprint |
| PUT | `/api/v1/blueprints/{author}/{bpname}/points` | 202 | Agrega un punto a un blueprint existente |

El controlador utiliza `BlueprintsServices` como única dependencia, respetando la separación de capas.

---

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

**Decisiones técnicas relevantes**

- **MapStruct** convierte entre el modelo de dominio (`Blueprint`/`Point`) y las entidades JPA (`BlueprintEntity`/`PointEmbeddable`), evitando acoplar el dominio a JPA.
- El `maven-compiler-plugin` configura `annotationProcessorPaths` con Lombok primero y MapStruct segundo, garantizando que Lombok genere getters/setters antes de que MapStruct los use. También activa `-parameters` para que MapStruct resuelva nombres de constructores.
- `addPoint` lleva `@Transactional` porque `@ElementCollection` es lazy por defecto; sin una transacción activa al leer la lista, Hibernate lanza `LazyInitializationException`.

---

### 3. Buenas prácticas de API REST

**Path base versionado**

El controlador usa `@RequestMapping("/api/v1/blueprints")`. El prefijo `/api/v1` es una buena práctica de versionamiento que permite introducir `/api/v2` en el futuro sin romper clientes existentes.

**Códigos HTTP correctos**

| Situación | Código | Descripción |
|-----------|--------|-------------|
| Consulta exitosa | `200 OK` | GET exitoso |
| Recurso creado | `201 Created` | POST exitoso |
| Actualización aceptada | `202 Accepted` | PUT exitoso |
| Datos inválidos / duplicado | `400 Bad Request` | Validación fallida o blueprint ya existe |
| Recurso no encontrado | `404 Not Found` | Author o blueprint inexistente |

**Clase genérica `ApiResponse<T>`**

Todas las respuestas siguen el mismo contrato JSON:

```java
public record ApiResponse<T>(int code, String message, T data) {}
```

Ejemplo de respuesta exitosa:
```json
{
  "code": 200,
  "message": "execute ok",
  "data": {
    "author": "john",
    "name": "house",
    "points": [{"x": 0, "y": 0}, {"x": 10, "y": 0}]
  }
}
```

Ejemplo de error 404:
```json
{
  "code": 404,
  "message": "Blueprint not found: john/unknown",
  "data": null
}
```

**`GlobalExceptionHandler`**

El manejo de errores se centraliza en `GlobalExceptionHandler` con `@RestControllerAdvice`, separándolo del controlador:

- `BlueprintNotFoundException` → 404
- `BlueprintPersistenceException` → 400
- `MethodArgumentNotValidException` (falla de `@Valid`) → 400 con detalle de campo

Gracias a esto, el controlador no tiene ningún `try-catch`: solo lógica de negocio.

---

### 4. OpenAPI / Swagger

La dependencia `springdoc-openapi-starter-webmvc-ui` (v2.6.0) ya está en el `pom.xml`. Al arrancar la aplicación, la documentación está disponible automáticamente en:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- JSON OpenAPI: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

**Configuración (`OpenApiConfig`)**

```java
@Bean
public OpenAPI api() {
    return new OpenAPI().info(new Info()
            .title("ARSW Blueprints API")
            .version("v1")
            .description("Blueprints Laboratory (Java 21 / Spring Boot 3.3.x)"));
}
```

**Anotaciones en el controlador**

Cada endpoint lleva `@Operation(summary = "...")` y `@ApiResponse(responseCode = "...", description = "...")` para documentar los posibles códigos de respuesta. El nombre `@ApiResponse` de Swagger colisiona con nuestro record `ApiResponse<T>`, por lo que se usa la ruta completa `@io.swagger.v3.oas.annotations.responses.ApiResponse` para la anotación.

```java
@Operation(summary = "Get a specific blueprint by author and name")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint not found")
@GetMapping("/{author}/{bpname}")
public ResponseEntity<ApiResponse<Blueprint>> byAuthorAndName(...) { ... }
```

---

### 5. Filtros de *Blueprints*

**Implementaciones**

| Filtro | Perfil | Comportamiento |
|--------|--------|---------------|
| `IdentityFilter` | (ninguno — default) | Devuelve el blueprint sin cambios |
| `RedundancyFilter` | `redundancy` | Elimina puntos **consecutivos duplicados** `(x,y)` |
| `UndersamplingFilter` | `undersampling` | Conserva **1 de cada 2 puntos** (índices pares) |

El filtro solo se aplica en `BlueprintsServices.getBlueprint()` (endpoint `GET /{author}/{bpname}`), no en los demás endpoints.

**Ejemplo RedundancyFilter**

Entrada: `[(0,0), (0,0), (5,5), (10,10), (10,10)]`  
Salida:  `[(0,0), (5,5), (10,10)]`

**Ejemplo UndersamplingFilter**

Entrada: `[(0,0), (1,1), (2,2), (3,3), (4,4)]`  
Salida:  `[(0,0), (2,2), (4,4)]`

**Gestión de conflictos entre filtros**

`IdentityFilter` lleva `@Profile("!redundancy & !undersampling")`: solo se registra como bean cuando ningún perfil de filtro está activo. Así se evita `NoUniqueBeanDefinitionException` al combinar perfiles.

---

## Combinaciones de perfiles disponibles

Los perfiles de **persistencia** (`postgres`, `memory`) y de **filtro** (`redundancy`, `undersampling`) son independientes y se pueden combinar libremente.

> En PowerShell los argumentos `-D` con puntos deben ir entre comillas.

```powershell
# PostgreSQL + sin filtro (default)
mvn spring-boot:run

# PostgreSQL + RedundancyFilter
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres,redundancy"

# PostgreSQL + UndersamplingFilter
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres,undersampling"

# Memoria + sin filtro
mvn spring-boot:run "-Dspring-boot.run.profiles=memory"

# Memoria + RedundancyFilter
mvn spring-boot:run "-Dspring-boot.run.profiles=memory,redundancy"

# Memoria + UndersamplingFilter
mvn spring-boot:run "-Dspring-boot.run.profiles=memory,undersampling"
```

Levantar PostgreSQL con Docker antes de usar el perfil `postgres`:
```bash
docker run --name blueprints-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=blueprints \
  -p 5432:5432 -d postgres:16
```

---

## Conclusión

El laboratorio aplica tres patrones de diseño de forma coherente:

1. **Puerto/Adaptador** — `BlueprintPersistence` es el puerto; `InMemoryBlueprintPersistence` y `PostgresBlueprintPersistence` son adaptadores intercambiables por perfil de Spring. Cambiar la tecnología de persistencia no modifica ninguna otra capa.

2. **Estrategia (Strategy)** — `BlueprintsFilter` es la estrategia; `IdentityFilter`, `RedundancyFilter` y `UndersamplingFilter` son implementaciones activables por perfil. Agregar un nuevo filtro solo requiere crear una clase con `@Profile` y `@Component`.

3. **Respuesta uniforme** — `ApiResponse<T>` garantiza que todos los endpoints, tanto exitosos como erróneos, devuelven el mismo contrato JSON, facilitando el consumo por parte de clientes frontend o mobile.

La combinación de Spring Profiles para persistencia y filtros permite arrancar la aplicación en cualquier contexto (desarrollo sin base de datos, producción con PostgreSQL, pruebas con filtros específicos) sin modificar el código fuente.
