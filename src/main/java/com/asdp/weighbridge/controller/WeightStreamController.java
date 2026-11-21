package com.asdp.weighbridge.controller;

import com.asdp.weighbridge.dto.WeightReadingDto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/weight")
@CrossOrigin(origins = "*")
public class WeightStreamController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicReference<WeightReadingDto> currentReading = new AtomicReference<>();
    private final Random random = new Random();

    @Value("${weighbridge.scale.device-id:}")
    private String deviceId;

    public WeightStreamController() {
        // Initialize default baseline weight
        this.currentReading.set(WeightReadingDto.builder()
                .weight(0.0f)
                .isStable(true)
                .isConnected(true)
                .unit("kg")
                .deviceId(null)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @PostConstruct
    public void init() {
        if (this.deviceId != null && !this.deviceId.isBlank()) {
            WeightReadingDto initial = this.currentReading.get();
            if (initial != null) {
                this.currentReading.set(WeightReadingDto.builder()
                        .weight(initial.getWeight())
                        .isStable(initial.getIsStable())
                        .isConnected(initial.getIsConnected())
                        .unit(initial.getUnit())
                        .deviceId(this.deviceId)
                        .timestamp(initial.getTimestamp())
                        .build());
            }
        }
    }

    /**
     * Endpoint SSE Server-Sent Events untuk streaming data berat real-time ke frontend loket dan pos timbang.
     * Sesuai Technical Req Jembatan Timbang.pdf Halaman 1 & Halaman 9.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWeight() {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Kirim initial reading seketika
        try {
            emitter.send(SseEmitter.event()
                    .name("weight-update")
                    .data(currentReading.get()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Simulasi telemetri hardware jembatan timbang (aktif secara default untuk development/testing).
     * Dapat diganti dengan serial port RS232 / TCP listener di lingkungan operasional pelabuhan.
     */
    @Scheduled(fixedRate = 300)
    public void broadcastTelemetry() {
        if (emitters.isEmpty()) {
            return;
        }

        // Simulasi pembacaan truk masuk timbangan (~10.210 kg stabil sesuai mockup Halaman 7)
        float baseWeight = 10210.0f;
        float jitter = (random.nextFloat() - 0.5f) * 10.0f;
        boolean isStable = Math.abs(jitter) < 4.0f;

        WeightReadingDto reading = WeightReadingDto.builder()
                .weight(baseWeight + jitter)
                .isStable(isStable)
                .isConnected(true)
                .unit("kg")
                .deviceId(deviceId)
                .timestamp(System.currentTimeMillis())
                .build();

        this.currentReading.set(reading);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("weight-update")
                        .data(reading));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Method untuk menerima injeksi nilai timbangan manual atau dari serial driver hardware.
     */
    public void updateWeightReading(float weight, boolean isStable, boolean isConnected) {
        WeightReadingDto reading = WeightReadingDto.builder()
                .weight(weight)
                .isStable(isStable)
                .isConnected(isConnected)
                .unit("kg")
                .deviceId(deviceId)
                .timestamp(System.currentTimeMillis())
                .build();
        this.currentReading.set(reading);
    }
}
