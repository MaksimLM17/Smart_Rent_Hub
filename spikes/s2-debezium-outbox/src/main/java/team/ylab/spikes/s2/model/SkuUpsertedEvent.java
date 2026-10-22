package team.ylab.spikes.s2.model;

public record SkuUpsertedEvent(
    String skuId, String name, String brand, String model, String categoryPath, String priceFrom) {}
