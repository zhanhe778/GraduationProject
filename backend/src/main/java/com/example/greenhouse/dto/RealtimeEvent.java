package com.example.greenhouse.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RealtimeEvent {
    String kind;
    Object payload;
}
