package top.tradesystem.krx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record KrxIsuBaseInfoResponse(
        @JsonProperty("OutBlock_1")
        List<Map<String, String>> outBlock1
) {}
