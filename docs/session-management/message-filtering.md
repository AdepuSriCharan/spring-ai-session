# Message Filtering

`SessionMessageFilter` controls which messages are appended to session memory by
`SessionMemoryAdvisor`. It is applied when the advisor persists the current request
message in `before()` and the model response in `after()`.

Messages rejected by this filter never enter the session event log, so they will not be
replayed into later turns. History retrieval is still governed by
[`EventFilter`](event-filtering.md); the two abstractions are intentionally separate.
For read-side prompt filtering, see [Context Filtering](context-filtering.md).

---

## Static factory shortcuts

For common cases, `SessionMessageFilter` exposes a small set of factories:

```java
// Match every message
SessionMessageFilter.includeAll();

// Keep only user and assistant messages
SessionMessageFilter.onlyUserAndAssistant();

// Drop tool response payloads from session memory
SessionMessageFilter.excludeToolMessages();

// Preserve the Bedrock-safe empty-assistant suppression from issue #19
SessionMessageFilter.excludeEmptyAssistantMessages();
```

---

## Builder usage

Wire the filter into `SessionMemoryAdvisor` with `messageFilter(...)`:

```java
SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService)
    .messageFilter(SessionMessageFilter.excludeEmptyAssistantMessages()
        .and(SessionMessageFilter.excludeToolMessages()))
    .build();
```

The default builder filter is `SessionMessageFilter.excludeEmptyAssistantMessages()`.
If you replace it, compose the default filter into your custom rule whenever you still
want the Bedrock-safe behavior.

---

## Content-aware filtering

Because `SessionMessageFilter` is a functional interface, you can use a lambda to inspect
message content and request context together:

```java
SessionMessageFilter filter = (message, context) -> {
    if (message.getMessageType() == MessageType.TOOL) {
        return false;
    }
    String text = message.getText();
    return text == null || !text.contains("DROP");
};
```

This makes it easy to keep large tool payloads, diagnostic traces, or other transient
content out of session storage without changing the underlying repository SPI.
