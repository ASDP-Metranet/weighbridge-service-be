package com.asdp.weighbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightReadingDto implements Serializable {
    private Float weight;
    private Boolean isStable;
    private Boolean isConnected;
    private String unit;
    private Long timestamp;
}
