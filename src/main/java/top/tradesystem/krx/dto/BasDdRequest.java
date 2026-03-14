package top.tradesystem.krx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Shared request DTO for any endpoint that requires a single basDd (base date) parameter.
 */
public record BasDdRequest(
        @NotBlank(message = "basDd is required")
        @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
        String basDd
) {}
