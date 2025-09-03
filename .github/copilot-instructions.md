# AirSight - AI Agent Instructions

## Project Overview
AirSight is a real-time air quality monitoring system with Spring Boot 2.7.14 + Java 21 backend, vanilla HTML/CSS/JS frontend, and MySQL database. It fetches data from OpenAQ API every 5 minutes, provides public AQI access, and premium features (historical data, PDF exports, SMS alerts) for registered users.

## Architecture
- **Backend**: Spring Boot REST APIs in `src/main/java/com/air/airquality/`
- **Frontend**: Separated files in `frontend/` - `index.html`, `styles.css`, `script.js`
- **Database**: MySQL with 3 tables: `users`, `aqi_data`, `user_alerts`
- **Scheduled Tasks**: `ScheduledService` runs every 5 minutes via `@Scheduled(fixedRate = 300000)`

## Key Components

### Controllers (REST API Endpoints)
- `AqiController`: Public (`/api/aqi/*`) and protected endpoints (`/api/aqi/historical`)
- `AuthController`: Registration/login with simple header-based auth (`Authorization: Basic <username:password>`)
- `UserController`: Profile management with `X-User-Id` header authentication
- `AlertController`: SMS alert management via Twilio integration
- `DataExportController`: PDF/CSV generation for authenticated users

### Services & Data Flow
- `OpenAQService`: Fetches from OpenAQ API, manages database-driven city list, provides fallback data
- `AlertService`: Processes threshold breaches, sends SMS via Twilio (optional, configured in properties)
- `ScheduledService`: Orchestrates data fetching and alert processing every 5 minutes

### Frontend Integration
- API base URL: `http://localhost:8080/api`
- Authentication: Uses `sessionStorage` for user sessions and header-based auth
- Dynamic city search with auto-add functionality via `/api/aqi/cities/add`
- Real-time UI updates with fallback data when API fails

## Developer Workflow

### Build & Run
```bash
mvn clean compile                    # Check compilation
mvn spring-boot:run                  # Start backend (port 8080)
# Frontend: Open frontend/index.html in browser
```

### Database Setup
```bash
mysql -u root -p < database_setup.sql  # Complete schema with sample data
```

### Configuration Patterns
- `application.properties`: Contains duplicated sections (cleaned up), uses custom properties like `alert.batch.size`
- Authentication: Header-based, not JWT - check `SecurityConfig.java`
- CORS enabled for `http://localhost:3000,http://127.0.0.1:5500`

## Critical Integration Points
- **OpenAQ API**: Handles rate limiting, failures gracefully with fallback data
- **Database-driven cities**: Unlike typical hardcoded lists, cities are managed dynamically
- **SMS Integration**: Optional Twilio setup - leave credentials empty to disable
- **Frontend-Backend Auth**: Simple header passing, stored in browser sessionStorage

## Common Patterns
- **Error Handling**: `GlobalExceptionHandler` with standardized JSON responses
- **Data Models**: JPA entities in `model/` with proper relationships
- **Repository Pattern**: Custom queries in repositories like `findByUserIdOrderByTimestampDesc`
- **Scheduled Jobs**: Use `@EnableScheduling` and `@Scheduled` annotations

## Testing & Debugging
- Spring Boot dev tools enabled for hot reload
- SQL logging enabled via `spring.jpa.show-sql=true`
- Frontend uses browser console logging and notification system
- Database connection pool configured for development (Hikari)

When working on this codebase, focus on the database-driven city management, header-based authentication pattern, and the clean separation between public/premium features.
