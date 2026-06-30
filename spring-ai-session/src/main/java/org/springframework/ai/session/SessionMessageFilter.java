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
 * Predicate that decides whether a {@link Message} should be appended to session memory.
 *
 * <p>
 * This filter is applied by {@link org.springframework.ai.session.advisor.SessionMemoryAdvisor}
 * when persisting the current request message and the model response. It does not affect
 * history retrieval; that remains the responsibility of {@link EventFilter}.
 *
 * <p>
 * Filters are composable via {@link #and(SessionMessageFilter)}, {@link #or(SessionMessageFilter)}
 * and {@link #negate()}. Use the static factories for common cases, or supply a lambda for
 * content-aware rules that also inspect the request/response context.
 *
 * <pre>{@code
 * SessionMessageFilter filter = SessionMessageFilter.excludeToolMessages()
 *     .and(SessionMessageFilter.excludeEmptyAssistantMessages());
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@FunctionalInterface
public interface SessionMessageFilter {

	/**
	 * Returns {@code true} if the given message should be persisted into session memory.
	 * @param message the message being considered
	 * @param context the advisor context for the current request/response
	 */
	boolean matches(Message message, Map<String, @Nullable Object> context);

	/**
	 * Returns a filter that matches only when both this filter and {@code other} match.
	 */
	default SessionMessageFilter and(SessionMessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return (message, context) -> this.matches(message, context) && other.matches(message, context);
	}

	/**
	 * Returns a filter that matches when either this filter or {@code other} matches.
	 */
	default SessionMessageFilter or(SessionMessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return (message, context) -> this.matches(message, context) || other.matches(message, context);
	}

	/**
	 * Returns a filter that matches when this filter does not.
	 */
	default SessionMessageFilter negate() {
		return (message, context) -> !this.matches(message, context);
	}

	/**
	 * Matches every message.
	 */
	static SessionMessageFilter includeAll() {
		return (message, context) -> true;
	}

	/**
	 * Matches only the supplied message types.
	 */
	static SessionMessageFilter includeMessageTypes(Set<MessageType> messageTypes) {
		Assert.notNull(messageTypes, "messageTypes must not be null");
		Set<MessageType> allowedMessageTypes = Set.copyOf(messageTypes);
		return (message, context) -> allowedMessageTypes.contains(message.getMessageType());
	}

	/**
	 * Rejects the supplied message types.
	 */
	static SessionMessageFilter excludeMessageTypes(Set<MessageType> messageTypes) {
		Assert.notNull(messageTypes, "messageTypes must not be null");
		Set<MessageType> excludedMessageTypes = Set.copyOf(messageTypes);
		return (message, context) -> !excludedMessageTypes.contains(message.getMessageType());
	}

	/**
	 * Matches only user and assistant messages.
	 */
	static SessionMessageFilter onlyUserAndAssistant() {
		return includeMessageTypes(Set.of(MessageType.USER, MessageType.ASSISTANT));
	}

	/**
	 * Rejects tool response messages. Tool responses are encoded as {@link MessageType#TOOL}
	 * and often carry large payloads that do not need to be replayed in memory.
	 */
	static SessionMessageFilter excludeToolMessages() {
		return excludeMessageTypes(Set.of(MessageType.TOOL));
	}

	/**
	 * Rejects system messages.
	 */
	static SessionMessageFilter excludeSystemMessages() {
		return excludeMessageTypes(Set.of(MessageType.SYSTEM));
	}

	/**
	 * Rejects empty assistant messages, i.e. assistant messages with blank text, no tool
	 * calls and no media. This preserves the Bedrock-safe behavior introduced for issue
	 * #19.
	 */
	static SessionMessageFilter excludeEmptyAssistantMessages() {
		return (message, context) -> !isEmptyAssistantMessage(message);
	}

	private static boolean isEmptyAssistantMessage(Message message) {
		return message instanceof AssistantMessage assistantMessage
				&& (assistantMessage.getText() == null || assistantMessage.getText().isBlank())
				&& !assistantMessage.hasToolCalls() && CollectionUtils.isEmpty(assistantMessage.getMedia());
	}

}
