package com.xinfei.optimizer.api.dto;

import com.xinfei.optimizer.domain.EngineName;

import java.util.List;

public record EnginesResponse(
        EngineName defaultEngine,
        List<EngineInfoDto> engines
) {
}
