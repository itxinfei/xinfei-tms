package com.xinfei.optimizer.api;

import com.xinfei.optimizer.api.dto.EnginesResponse;
import com.xinfei.optimizer.application.LoadPlanApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizer")
public class EngineController {

    private final LoadPlanApplicationService applicationService;

    public EngineController(LoadPlanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/engines")
    public EnginesResponse engines() {
        return applicationService.engines();
    }
}
