# CollabEdit — React Frontend

React 18 + Vite + Tailwind CSS frontend for the collaborative document editor.

## Quick Start

```bash
cd frontend
npm install
npm run dev       # → http://localhost:3000  (proxies /api + /ws to :8080)
```

Make sure the Spring Boot backend is running on port 8080.

## Build for Production

```bash
npm run build     # outputs to ../src/main/resources/static/
```

The built files are served by Spring Boot at `http://localhost:8080/`.

## Project Structure

```
src/
├── main.jsx                          # Entry point
├── App.jsx                           # React Router setup
├── index.css                         # Tailwind + custom styles
│
├── api/
│   └── documentApi.js                # All REST API calls
│
├── hooks/
│   └── useWebSocket.js               # STOMP WebSocket hook
│
├── context/
│   └── UserContext.jsx                # Auth state (localStorage)
│
├── utils/
│   └── helpers.js                    # Colors, formatting, local storage
│
├── components/
│   ├── ui/
│   │   ├── Avatar.jsx                # Color-coded user avatar
│   │   ├── Modal.jsx                 # Reusable modal shell
│   │   └── Toast.jsx                 # Toast notifications
│   │
│   ├── editor/
│   │   ├── CollabEditor.jsx          # contentEditable + CRDT char-map
│   │   ├── EditorToolbar.jsx         # Title, presence, actions
│   │   └── StatusBar.jsx             # Connection, chars, online count
│   │
│   └── modals/
│       ├── ShareModal.jsx            # Share doc, view collaborators
│       └── HistoryPanel.jsx          # Version history, restore
│
└── pages/
    ├── LoginPage.jsx                 # Name entry
    ├── DashboardPage.jsx             # Document list + CRUD
    └── EditorPage.jsx                # Orchestrates editor + WebSocket
```

## API Coverage

Every endpoint from `DocumentController` and `WebSocketController` is used:

| Endpoint | Component |
|---|---|
| `POST /api/v1/documents` | DashboardPage → create |
| `GET /api/v1/documents/{id}` | DashboardPage → list, EditorPage → load |
| `PUT /api/v1/documents/{id}/title` | EditorToolbar → inline edit |
| `DELETE /api/v1/documents/{id}` | DashboardPage → delete button |
| `POST /api/v1/documents/{id}/share` | ShareModal → invite form |
| `GET /api/v1/documents/{id}/collaborators` | ShareModal → list |
| `GET /api/v1/documents/{id}/history` | HistoryPanel → load |
| `POST /api/v1/documents/{id}/restore` | HistoryPanel → restore |
| `GET /health` | documentApi (available) |
| WS `/app/document/{id}/join` | useWebSocket → on connect |
| WS `/app/document/{id}/operation` | useWebSocket → sendOperation |
| WS `/app/document/{id}/cursor` | useWebSocket → sendCursor |
| WS `/app/document/{id}/leave` | useWebSocket → on disconnect |
| WS `/topic/document/{id}` | useWebSocket → onOperation |
| WS `/topic/document/{id}/presence` | useWebSocket → onPresence |
| WS `/topic/document/{id}/ack/{uid}` | useWebSocket → onAck |
