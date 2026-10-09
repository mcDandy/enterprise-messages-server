# Database & Docker

Database uses PostgreSQL 17 with Spring Data JPA.

## Running

1. Install Docker Desktop.
2. Copy `.env.example` to `.env` and configure the variables.
3. Run:

```bash
docker compose up -d --build
```

This starts PostgreSQL, Adminer and two backend instances.

- Adminer: http://localhost:8081
- Backend 1: http://localhost:8082
- Backend 2: http://localhost:8083

The database tables are created from `database/schema.sql` on the first startup. Data is stored in a Docker volume, so it persists between restarts.

## Tests

Requires Java 21 and a running local PostgreSQL database.

On Windows:

```powershell
.\gradlew.bat test
```

## Notes

- Hibernate uses `ddl-auto=validate`, so it doesn't modify the database schema.
- The `domain` package contains JPA entities, and `repository` contains Spring Data repositories.
- Both backend instances use the same database. WebSocket communication between instances is not handled by Docker alone.