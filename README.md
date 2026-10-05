# ModelFlux

A resilient, multi-provider AI chat application. Instead of relying on a single LLM API, ModelFlux routes every chat request across multiple free-tier providers (Groq, Gemini, OpenRouter), automatically failing over to a healthy provider when one is rate-limited, down, or fails mid-stream — with live status visibility and manual override in the UI.

## Features

- **JWT authentication** — register/login with hashed passwords
- **Real-time streaming chat** — token-by-token responses over WebSocket (STOMP)
- **Multi-provider automatic failover** — routes around rate-limited or failing providers using live header-based rate-limit tracking (Redis) and circuit breakers (Resilience4j)
- **Streaming failover** — if a provider dies mid-response, the partial output is discarded and the reply restarts cleanly with the next healthy provider, with a visible "switching..." indicator
- **Manual provider override** — force a specific provider (Groq / Gemini / OpenRouter) or leave it on Auto
- **Live provider status** — real-time ACTIVE / RATE_LIMITED / DOWN badges in the UI
- **Conversation history** — persisted chats with sidebar navigation, pin/rename/delete (UI only, not yet backend-persisted)
- **Provider attribution** — every AI response is tagged with which provider actually answered

## Tech Stack

**Backend:** Spring Boot, Spring Security (JWT), Spring Data JPA, PostgreSQL, Redis, Resilience4j, WebSocket/STOMP

**Frontend:** React, Vite, Tailwind CSS, @stomp/stompjs

**AI Providers:** Groq, Google Gemini, OpenRouter (all free-tier)

## Architecture

User → React (WebSocket/STOMP) → Spring Boot
↓
ChatOrchestratorService
↓
Checks Redis (rate limits) + Circuit Breaker state
↓
Routes to healthy provider
(Groq → Gemini → OpenRouter)
↓
On failure → retry next provider automatically
↓
Response streamed back + saved to PostgreSQL


## Setup

### Prerequisites
- Java 17
- Node.js 18+
- Docker (for PostgreSQL and Redis)
- API keys: [Groq](https://console.groq.com), [Gemini](https://aistudio.google.com), [OpenRouter](https://openrouter.ai)

### 1. Start PostgreSQL and Redis

```bash
docker run --name modelflux-postgres -e POSTGRES_PASSWORD=yourpassword -e POSTGRES_DB=modelflux -p 5432:5432 -d postgres
docker run --name modelflux-redis -p 6379:6379 -d redis
```

### 2. Backend setup

```bash
cd modelflux
```

Create a `.env` file in the project root:

- JWT_SECRET=your-random-secret-at-least-32-characters-long
- DB_PASSWORD=yourpassword
- GROQ_API_KEY=your-groq-key
- GEMINI_API_KEY=your-gemini-key
- OPENROUTER_API_KEY=your-openrouter-key


Run the backend:
```bash
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080`.

### 3. Frontend setup

```bash
cd modelflux-frontend
npm install
```

Create a `.env` file:
- VITE_API_URL=http://localhost:8080
- VITE_WS_URL=ws://localhost:8080/ws


Run the frontend:
```bash
npm run dev
```

The app runs on `http://localhost:5173`.

## Repositories

- Backend: [modelflux-gateway](https://github.com/SachinPattar8866/modelflux-gateway)
- Frontend:[modelflux-frontend](https://github.com/SachinPattar8866/modelflux-frontend)
