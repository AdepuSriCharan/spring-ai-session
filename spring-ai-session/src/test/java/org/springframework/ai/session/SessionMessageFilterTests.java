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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SessionMessageFilter}.
 *
 * @author Christian Tzolov
 */
class SessionMessageFilterTests {

	@Test
	void onlyUserAndAssistantRejectsToolResponses() {
		SessionMessageFilter filter = SessionMessageFilter.onlyUserAndAssistant();
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "{\"temp\":\"22C\"}")))
			.build();

		assertThat(filter.matches(new UserMessage("hello"), Map.of())).isTrue();
		assertThat(filter.matches(new AssistantMessage("hello"), Map.of())).isTrue();
		assertThat(filter.matches(toolResponse, Map.of())).isFalse();
	}

	@Test
	void includeMessageTypesKeepsOnlyTheSpecifiedTypes() {
		SessionMessageFilter filter = SessionMessageFilter.includeMessageTypes(Set.of(MessageType.USER));
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "{\"temp\":\"22C\"}")))
			.build();

		assertThat(filter.matches(new UserMessage("hello"), Map.of())).isTrue();
		assertThat(filter.matches(new AssistantMessage("hello"), Map.of())).isFalse();
		assertThat(filter.matches(toolResponse, Map.of())).isFalse();
	}

	@Test
	void contentAndContextAwareLambdaCanFilterMessages() {
		SessionMessageFilter filter = (message, context) -> "persist".equals(context.get("mode"))
				&& (message.getText() == null || !message.getText().contains("DROP"));

		assertThat(filter.matches(new UserMessage("keep this"), Map.of("mode", "persist"))).isTrue();
		assertThat(filter.matches(new UserMessage("DROP this"), Map.of("mode", "persist"))).isFalse();
		assertThat(filter.matches(new UserMessage("keep this"), Map.of("mode", "skip"))).isFalse();
	}

	@Test
	void excludeEmptyAssistantMessagesRejectsBlankAssistantMessages() {
		SessionMessageFilter filter = SessionMessageFilter.excludeEmptyAssistantMessages();
		AssistantMessage emptyAssistant = new AssistantMessage("");
		AssistantMessage withToolCalls = AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{}")))
			.build();

		assertThat(filter.matches(emptyAssistant, Map.of())).isFalse();
		assertThat(filter.matches(withToolCalls, Map.of())).isTrue();
	}

}
