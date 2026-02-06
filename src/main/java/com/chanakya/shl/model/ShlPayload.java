package com.chanakya.shl.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShlPayload {

    private String url;

    private String key;

    @JsonProperty("exp")
    private Long exp;

    private String flag;

    private String label;

    @Builder.Default
    private int v = 1;
}
