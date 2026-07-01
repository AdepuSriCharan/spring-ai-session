# Context Filtering

`SessionContextFilter` controls which retrieved messages are injected into the prompt by
`SessionMemoryAdvisor`.

This is a read-side filter: it runs after the session history has been loaded and after
`EventFilter` has selected which events are available, but before those messages are
prepended to the current request.

Messages rejected by this filter can still remain in session storage. That makes the
filter useful for large tool responses, debug traces, or other content you want to keep
for auditing without replaying on every turn.

---

## Static factory shortcuts

For the most common cases, `SessionContextFilter` exposes a small set of factories:

```java
// Match every retrieved message
SessionContextFilter.includeAll();

// Keep only user and assistant messages
SessionContextFilter.onlyUserAndAssistant();

// Drop tool response payloads from the prompt
SessionContextFilter.excludeToolMessages();

// Preserve the Bedrock-safe empty-assistant suppression from issue #19
SessionContextFilter.excludeEmptyAssistantMessages();
```

---

## Builder usage

Wire the filter into `SessionMemoryAdvisor` with `contextFilter(...)`:

```java
SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService)
    .contextFilter(SessionContextFilter.excludeToolMessages())
    .build();
```

The default builder filter is `SessionContextFilter.includeAll()`, so existing
applications continue to replay the full retrieved history.

---

## Content-aware filtering

Because `SessionContextFilter` is a functional interface, you can use a lambda to inspect
message content and request context together:

```java
SessionContextFilter filter = (message, context) -> {
    if (message.getMessageType() == MessageType.TOOL) {
        return false;
    }
    String text = message.getText();
    return text == null || !text.contains("DROP");
};
```

This makes it easy to keep large tool payloads or noisy transient content out of the
prompt while still preserving the underlying session events.

---

## Relation to other filters

- `EventFilter` chooses which events are loaded from the repository.
- `SessionContextFilter` chooses which retrieved messages enter the prompt.
- `SessionMessageFilter` chooses which messages are written back to session storage.
