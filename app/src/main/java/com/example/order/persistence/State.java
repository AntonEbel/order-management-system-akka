package com.example.order.persistence;

import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor
@Data
public class State implements JsonSerializable {
    private Map<String, OrderItem> orders = new HashMap<>();

    public State addOrder(Order order) {
        orders.put(order.id, new OrderItem(order.items, order.state));
        return this;
    }

    public Optional<Order> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId))
                .map(orderItem -> new Order(orderId, orderItem.items, orderItem.state));
    }

    @AllArgsConstructor
    @NoArgsConstructor
    private final static class OrderItem implements JsonSerializable {
        public Map<String, Integer> items;
        public OrderState state;
    }

}
