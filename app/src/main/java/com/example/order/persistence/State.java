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
class State implements JsonSerializable {
    private Map<String, OrderItem> orders = new HashMap<>();

    public State addOrder(Order order) {
        orders.put(order.id, new OrderItem(order.items, order.state, order.fulfillmentResult));
        return this;
    }

    public Optional<Order> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId))
                .map(orderItem -> toOrder(orderId, orderItem));
    }

    public Order getOrder(String orderId) {
        return toOrder(orderId, orders.get(orderId));
    }

    private Order toOrder(String orderId, OrderItem orderItem) {
        return new Order(orderId, orderItem.items, orderItem.state, orderItem.fulfillmentResult);
    }

    public State changeState(String orderId, OrderState state) {
        orders.get(orderId).state = state;
        return this;
    }

    public State closeOrder(String orderId, FulfillmentResult fulfillmentResult) {
        OrderItem orderItem = orders.get(orderId);
        orderItem.state = OrderState.CLOSED;
        orderItem.fulfillmentResult = fulfillmentResult;
        return this;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    private final static class OrderItem implements JsonSerializable {
        public Map<String, Integer> items;
        public OrderState state;
        public FulfillmentResult fulfillmentResult;
    }

}
