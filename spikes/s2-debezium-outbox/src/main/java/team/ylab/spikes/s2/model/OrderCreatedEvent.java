package team.ylab.spikes.s2.model;

import java.util.List;

public record OrderCreatedEvent(
    String orderId, String customerId, String status, String totalAmount, List<String> items) {}
