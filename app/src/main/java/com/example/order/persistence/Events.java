package com.example.order.persistence;

import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class Events {

    interface Event extends JsonSerializable {

    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static final class OrderCreated implements Event {
        public Order order;
    }
}
