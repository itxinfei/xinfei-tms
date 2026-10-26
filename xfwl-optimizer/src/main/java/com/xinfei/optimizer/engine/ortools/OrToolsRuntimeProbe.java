package com.xinfei.optimizer.engine.ortools;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPSolver;
import com.xinfei.optimizer.config.OptimizerProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OR-Tools 原生库探测。默认不随服务启动加载，必须显式开关开启，避免本地/镜像缺原生库时拖垮规则引擎。
 */
@Component
public class OrToolsRuntimeProbe {

    private final OptimizerProperties properties;
    private final AtomicReference<ProbeResult> cached = new AtomicReference<>();

    public OrToolsRuntimeProbe(OptimizerProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.ortoolsEnabled();
    }

    public ProbeResult probe() {
        if (!properties.ortoolsEnabled()) {
            return ProbeResult.disabled(properties.engines().ortools().version());
        }
        ProbeResult existing = cached.get();
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cached.get() != null) {
                return cached.get();
            }
            ProbeResult result = doProbe();
            cached.set(result);
            return result;
        }
    }

    public void invalidate() {
        cached.set(null);
    }

    private ProbeResult doProbe() {
        String version = properties.engines().ortools().version();
        MPSolver solver = null;
        try {
            Loader.loadNativeLibraries();
            solver = MPSolver.createSolver("SCIP");
            if (solver == null) {
                return new ProbeResult(ProbeStatus.UNAVAILABLE, version,
                        "SCIP solver backend unavailable", Instant.now());
            }
            return new ProbeResult(ProbeStatus.AVAILABLE, version,
                    "native libraries loaded and SCIP solver created", Instant.now());
        } catch (Throwable t) {
            return new ProbeResult(ProbeStatus.UNAVAILABLE, version,
                    t.getClass().getSimpleName() + ": " + safeMessage(t), Instant.now());
        } finally {
            if (solver != null) {
                try {
                    solver.delete();
                } catch (Throwable ignored) {
                    // 删除 native handle 失败不影响探测结论
                }
            }
        }
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return "native load failed";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    public enum ProbeStatus {
        DISABLED,
        AVAILABLE,
        UNAVAILABLE
    }

    public record ProbeResult(
            ProbeStatus status,
            String version,
            String message,
            Instant checkedAt
    ) {
        static ProbeResult disabled(String version) {
            return new ProbeResult(ProbeStatus.DISABLED, version, "OR-Tools disabled by configuration", Instant.now());
        }

        public boolean available() {
            return status == ProbeStatus.AVAILABLE;
        }
    }
}
