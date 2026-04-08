# git-sat backend

Backend service for the `git-sat` CLI. This is a small Spring Boot API that accepts commit/file-change data and returns a summarized response.

**Need / Purpose**
- Provide a lightweight HTTP endpoint for the CLI to request summaries.
- Provide browser-friendly auth routes that issue an `HttpOnly` cookie named `git-sat`.

**Repo Structure**
- `pom.xml` Maven build with Spring Boot 3.x and Java 17.
- `src/main/java/com/gitsat/backend/GitSatApplication.java` Spring Boot entry point.
- `src/main/java/com/gitsat/backend/controller/SummaryController.java` REST controller with the `/summary` endpoint.
- `src/main/java/com/gitsat/backend/controller/AuthController.java` REST controller for register, signup, login, logout, and `me`.
- `src/main/java/com/gitsat/backend/dto/` Request/response DTOs used by the API.

**API Snapshot**
- `POST /auth/register`
- `POST /auth/signup`
  - Input: `RegisterRequest` with `name`, `email`, and `password`.
  - Output: `201 Created`, user info in the response body, and an `HttpOnly` cookie named `git-sat`.
- `POST /auth/login`
  - Input: `LoginRequest` with `email` and `password`.
  - Output: `200 OK`, user info in the response body, and a refreshed `git-sat` cookie.
- `POST /auth/logout`
  - Output: clears the `git-sat` cookie.
- `GET /auth/me`
  - Output: returns the authenticated user when the `git-sat` cookie is present and valid.
- `POST /auth/device-code`
- `POST /auth/activate-device`
- `POST /auth/verify-device-code`
- `POST /auth/refresh`
- `POST /summary`
  - Input: `SummaryRequest` with repo metadata and a list of commits/files.
  - Output: `SummaryResponse` containing per-file summaries, a short overall summary, and a detailed overall summary for the CLI.
  - Current behavior: summaries are generated through the NVIDIA-hosted OpenAI-compatible chat completions API.

**Build/Run**
- `mvn spring-boot:run`
- Configure `.env` with `NVIDIA_API_BASE`, `NVIDIA_API_KEY`, and `NVIDIA_MODEL` before calling `/summary`.
- Optional: set `NVIDIA_REQUEST_TIMEOUT_SECONDS` in `.env` to control the upstream LLM request timeout. The default is `120`.
- Set `AUTH_TOKEN_SECRET` in `.env` if you want auth cookies to stay valid across application restarts.
- Auth data is stored in MongoDB, so set `MONGODB_URI` in `.env` before starting the app.

**Auth Notes**
- Registered users and device codes are stored in MongoDB and persist across application restarts.
- Passwords are stored as BCrypt hashes, not plain text.
- The backend keeps `createdAt` and `lastLoginAt` timestamps for each user document.
- Cookies are issued as `HttpOnly`, with `SameSite` and `Secure` controlled from properties/env vars.
- For browser clients on another origin, send requests with credentials enabled so the `git-sat` cookie is stored and sent back.

**Smoke Test**
- Run `mvn test` for the local auth and application tests.
- Run `mvn -DrunNvidiaApiTest=true -Dtest=NvidiaApiSmokeTest test` to verify `/summary` can reach the configured NVIDIA API and return non-fallback summaries.

**Docker**
- Build: `docker build -t git-sat-backend .`
- Run: `docker run --rm -p 8080:8080 git-sat-backend`

