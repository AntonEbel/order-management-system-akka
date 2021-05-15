package com.example.order.persistence;

import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public final class Order implements JsonSerializable {
    public String id;
    public Map<String, Integer> items;
    public OrderState state;
}
