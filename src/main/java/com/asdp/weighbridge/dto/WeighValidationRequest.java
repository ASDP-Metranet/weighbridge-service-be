package com.asdp.weighbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeighValidationRequest {

    @NotBlank(message = "Kode Barcode (Kode Booking / No. Tiket / Order ID) tidak boleh kosong.")
    private String barcodeInput;

    private String searchType;

    @NotNull(message = "Bobot timbangan harus terisi.")
    private Float actualWeight;

    private Boolean isStable;

    private String operatorUsername;
}
