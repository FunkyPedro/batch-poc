package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

public class EventBridgeMessageHandler implements MessageHandler {

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private String eventBusName = "default";

    public EventBridgeMessageHandler(EventBridgeClient eventBridgeClient) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            PutEventsRequestEntry.Builder eventBuilder = PutEventsRequestEntry.builder();

            // Extract headers for EventBridge event properties
            String source = getHeaderValue(message, "source", "spring-integration");
            String detailType = getHeaderValue(message, "eventType", "Spring Integration Event");
            String eventBusName = getHeaderValue(message, "eventBusName", this.eventBusName);

            // Convert payload to JSON string
          String detail = objectMapper.writeValueAsString(message.getPayload());
          //  Object test = message.getPayload();

            // Build the event
            PutEventsRequestEntry event = eventBuilder
                    .source(source)
                    .detailType(detailType)
                    .detail(message.getPayload().toString())
                    .eventBusName(eventBusName)
                    .build();

            // Send to EventBridge
            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(event)
                    .build();

            PutEventsResponse response = eventBridgeClient.putEvents(request);

            // Check for failures
            if (response.failedEntryCount() > 0) {
                throw new MessagingException("Failed to send event to EventBridge: " +
                        response.entries().get(0).errorMessage());
            }

        } catch (Exception e) {
            throw new MessagingException("Error sending message to EventBridge", e);
        }
    }
    private String getHeaderValue(Message<?> message, String headerName, String defaultValue) {
        Object value = message.getHeaders().get(headerName);
        return value != null ? value.toString() : defaultValue;
    }

    public void setEventBusName(String eventBusName) {
        this.eventBusName = eventBusName;
    }
}
