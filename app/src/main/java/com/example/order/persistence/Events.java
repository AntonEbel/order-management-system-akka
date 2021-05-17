package com.example.order.persistence;

import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

class Events {

    interface Event extends JsonSerializable {

    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static final class OrderCreated implements Event {
        public Order order;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static final class OrderStateChanged implements Event {
        public String orderId;
        public OrderState state;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static final class OrderClosed implements Event {
        public String orderId;
        public FulfillmentResult fulfillmentResult;
    }
}
