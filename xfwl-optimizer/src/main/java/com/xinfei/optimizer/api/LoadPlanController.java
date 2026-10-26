package com.xinfei.optimizer.api;

import com.xinfei.optimizer.api.dto.LoadPlanSolveResponse;
import com.xinfei.optimizer.api.dto.SolveLoadPlanRequest;
import com.xinfei.optimizer.application.LoadPlanApplicationService;
import com.xinfei.optimizer.domain.OptimizerUnsupportedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizer")
public class LoadPlanController {

    private final LoadPlanApplicationService applicationService;

    public LoadPlanController(LoadPlanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/load-plans/solve")
    public LoadPlanSolveResponse solveLoadPlan(@Valid @RequestBody SolveLoadPlanRequest request) {
        return applicationService.solveLoadPlan(request);
    }

    @PostMapping("/routes/solve")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void solveRoute() {
        throw new OptimizerUnsupportedException("车辆路径/时间窗求解在容量配载试点稳定后开启");
    }

    @PostMapping("/schedules/solve")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void solveSchedule() {
        throw new OptimizerUnsupportedException("司机/月台/波次排班属于 P2 Timefold 评估范围");
    }

    @PostMapping("/replay")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void replay() {
        throw new OptimizerUnsupportedException("历史快照回放需要等 disp_optimization_job/solution 落库后开启");
    }
}
