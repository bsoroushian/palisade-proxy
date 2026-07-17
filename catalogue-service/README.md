# Mock API Service (Gateway & WAF Test Target)

A lightweight Kotlin and Ktor-based mock API service. It provides a standard user management schema to test and validate API gateway routing, reverse proxy configurations, and WAF security guards.

## 🚀 Quick Start

This project uses Gradle. You can spin up the application locally using the embedded H2 in-memory database.

```bash
# Clone the repository and navigate to the directory
cd palisade-proxy/catalogue-service

# Run the server locally
./gradlew run
```

The server will start, automatically spin up an **H2 In-Memory Database**, and seed initial mock user records.

## 📡 API Endpoints & Auth Matrix

All data mutations are stored in-memory and will reset when the server stops.

| Method | Path | Auth Required? | Rules / Limitations |
| :--- | :--- | :--- | :--- |
| `GET` | `/v1/user` | No | Returns all seeded users. |
| `GET` | `/v1/user/{id}` | No | Returns a specific user profile. |
| `POST` | `/v1/user` | **Basic Auth** | Creates a new user record. No role validation. |
| `PUT` | `/v1/user` | **Basic Auth** | Updates user record. **Prevents mutating other users' data.** |
| `DELETE` | `/v1/user/{id}` | **Basic Auth** | Deletes a user profile. *(Recommended for WAF/IDOR testing)* |
| `GET` | `/openai` | No | Mock endpoint for external AI service routing. |

## 🛠️ Tech Stack & Architecture

* **Framework**: Ktor (Kotlin asynchronous server framework)
* **ORM**: JetBrains Exposed
* **Database**: H2 Database (configured for in-memory operation)

## ⚠️ Security & Production Limitations

* **No Persistence**: Restarting the application wipes all database changes and resets the state to the original seed data.
* **Flat Auth Model**: The service lacks an administrative role layer. Any authenticated user can execute a `POST` or `DELETE` request.
* **Basic Auth Only**: Designed strictly for local gateway testing. Do not expose this service directly to the public internet without the gateway guard active.
