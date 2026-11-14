# spring-ai-chat-engine

Backend-only conversational AI REST API.

## Stack
- Java 21, Spring Boot 4.1.2, Spring AI 2.0.1 (Jackson 3: use `tools.jackson.*` for databind), Maven
- Web starter is `spring-boot-starter-webmvc`
- PostgreSQL 16, Flyway, Spring Data JPA, Lombok (never `@Data` on entities)
- LLM: Groq via the OpenAI-compatible starter (`spring-ai-starter-model-openai`,
  `spring.ai.openai.base-url=https://api.groq.com/openai/v1`). No OpenAI account is used.
- Package: `com.agentworkshq.chatengine` with subpackages config, controller, service,
  repository, entity, dto, exception

## Rules
- Do NOT write Spring Boot 3 or Spring AI 1.x code. If unsure about an API, check the
  Spring AI 2.0 docs or the dependency sources before guessing.
- Use records for DTOs. Constructor injection only.
- Never hold a DB transaction open during an LLM call.
- Never log or return API keys or raw provider errors.
- Tests must never call the real Groq API.
- Every step must end with: `./mvnw clean verify` passing and the app starting
  against docker-compose Postgres.
- Commit after each completed step with a conventional commit message.

## Commands
- DB: `docker compose up -d postgres`
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw clean verify`