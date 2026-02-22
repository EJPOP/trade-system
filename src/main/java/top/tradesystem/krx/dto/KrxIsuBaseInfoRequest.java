package top.tradesystem.krx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KrxIsuBaseInfoRequest(
        @NotBlank(message = "basDd is required")
        @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
        String basDd
) {}
