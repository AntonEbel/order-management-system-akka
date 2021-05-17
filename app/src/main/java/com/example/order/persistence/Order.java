package com.example.order.persistence;

import com.example.order.serialization.JsonSerializable;
import lombok.*;

import java.util.Map;

@AllArgsConstructor
@Data
@Builder(toBuilder = true)
public final class Order implements JsonSerializable {
    public String id;
    public Map<String, Integer> items;
    public OrderState state;
    public FulfillmentResult fulfillmentResult;
}
