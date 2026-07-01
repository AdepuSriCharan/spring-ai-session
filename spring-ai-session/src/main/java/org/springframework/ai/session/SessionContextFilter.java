/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.session;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Predicate that decides whether a retrieved {@link Message} should be injected into the
 * prompt context.
 *
 * <p>
 * This filter is applied by {@link org.springframework.ai.session.advisor.SessionMemoryAdvisor}
 * after the session history is loaded and before those messages are prepended to the
 * current prompt. It does not affect storage; that remains the responsibility of
 * {@link SessionMessageFilter}. History retrieval is still governed by {@link EventFilter}.
 *
 * <p>
 * Filters are composable via {@link #and(SessionContextFilter)}, {@link #or(SessionContextFilter)}
 * and {@link #negate()}. Use the static factories for common cases, or supply a lambda for
 * content-aware rules that also inspect the request context.
 *
 * <pre>{@code
 * SessionContextFilter filter = SessionContextFilter.excludeToolMessages()
 *     .and(SessionContextFilter.excludeEmptyAssistantMessages());
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@FunctionalInterface
public interface SessionContextFilter {

	/**
	 * Returns {@code true} if the given message should be injected into the prompt.
	 * @param message the retrieved message being considered
	 * @param context the advisor context for the current request/response
	 */
	boolean matches(Message message, Map<String, @Nullable Object> context);

	/**
	 * Returns a filter that matches only when both this filter and {@code other} match.
	 */
	default SessionContextFilter and(SessionContextFilter other) {
		Assert.notNull(other, "other must not be null");
		return (message, context) -> this.matches(message, context) && other.matches(message, context);
	}

	/**
	 * Returns a filter that matches when either this filter or {@code other} matches.
	 */
	default SessionContextFilter or(SessionContextFilter other) {
		Assert.notNull(other, "other must not be null");
		return (message, context) -> this.matches(message, context) || other.matches(message, context);
	}

	/**
	 * Returns a filter that matches when this filter does not.
	 */
	default SessionContextFilter negate() {
		return (message, context) -> !this.matches(message, context);
	}

	/**
	 * Matches every message.
	 */
	static SessionContextFilter includeAll() {
		return (message, context) -> true;
	}

	/**
	 * Matches only the supplied message types.
	 */
	static SessionContextFilter includeMessageTypes(Set<MessageType> messageTypes) {
		Assert.notNull(messageTypes, "messageTypes must not be null");
		Set<MessageType> allowedMessageTypes = Set.copyOf(messageTypes);
		return (message, context) -> allowedMessageTypes.contains(message.getMessageType());
	}

	/**
	 * Rejects the supplied message types.
	 */
	static SessionContextFilter excludeMessageTypes(Set<MessageType> messageTypes) {
		Assert.notNull(messageTypes, "messageTypes must not be null");
		Set<MessageType> excludedMessageTypes = Set.copyOf(messageTypes);
		return (message, context) -> !excludedMessageTypes.contains(message.getMessageType());
	}

	/**
	 * Matches only user and assistant messages.
	 */
	static SessionContextFilter onlyUserAndAssistant() {
		return includeMessageTypes(Set.of(MessageType.USER, MessageType.ASSISTANT));
	}

	/**
	 * Rejects tool response messages. Tool responses are encoded as {@link MessageType#TOOL}
	 * and often carry large payloads that do not need to be replayed in the prompt.
	 */
	static SessionContextFilter excludeToolMessages() {
		return excludeMessageTypes(Set.of(MessageType.TOOL));
	}

	/**
	 * Rejects system messages.
	 */
	static SessionContextFilter excludeSystemMessages() {
		return excludeMessageTypes(Set.of(MessageType.SYSTEM));
	}

	/**
	 * Rejects empty assistant messages, i.e. assistant messages with blank text, no tool
	 * calls and no media. This is useful when older sessions already contain empty frames
	 * that should not re-enter the prompt.
	 */
	static SessionContextFilter excludeEmptyAssistantMessages() {
		return (message, context) -> !isEmptyAssistantMessage(message);
	}

	private static boolean isEmptyAssistantMessage(Message message) {
		return message instanceof AssistantMessage assistantMessage
				&& (assistantMessage.getText() == null || assistantMessage.getText().isBlank())
				&& !assistantMessage.hasToolCalls() && CollectionUtils.isEmpty(assistantMessage.getMedia());
	}

}
