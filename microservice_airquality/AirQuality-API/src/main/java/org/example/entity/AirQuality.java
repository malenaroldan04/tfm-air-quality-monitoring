package org.example.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class AirQuality {
    private LocalDateTime timestamp;
    private double temperature;
    private double humidity;
    private double pressure;
    private double iaq;
    private double co2;
    private double voc;
}
