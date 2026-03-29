package top.tradesystem.krx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import top.tradesystem.krx.common.DateConstants;

/**
 * Shared request DTO for any endpoint that requires a single basDd (base date) parameter.
 * 형식(\d{8}) + 달력 유효성(20240230 거부) 동시 검증.
 */
public record BasDdRequest(
        @NotBlank(message = "basDd is required")
        @Pattern(regexp = "\\d{8}", message = "basDd must be YYYYMMDD")
        String basDd
) {
    /** compact constructor — strict 달력 검증 추가. */
    public BasDdRequest {
        if (basDd != null && basDd.matches("\\d{8}")) {
            DateConstants.validateBasDd(basDd);
        }
    }
}
