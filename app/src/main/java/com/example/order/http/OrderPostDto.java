package com.example.order.http;

import lombok.Data;

import java.util.Map;

@Data
public class OrderPostDto {
    private Map<String, Integer> items;
}
