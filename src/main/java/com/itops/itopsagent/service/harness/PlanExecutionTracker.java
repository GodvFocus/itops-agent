package com.itops.itopsagent.service.harness;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PlanExecutionTracker {

    private final ConcurrentHashMap<String, PlanProgress> progressMap = new ConcurrentHashMap<>();

    public void register(String planId, Set<Integer> stepNos) {
        progressMap.put(planId, new PlanProgress(stepNos));
    }

    public boolean markStepCompleted(String planId, Integer stepNo) {
        PlanProgress progress = progressMap.get(planId);
        if (progress == null || progress.failed) {
            return false;
        }
        progress.completedSteps.add(stepNo);
        if (progress.completedSteps.containsAll(progress.expectedSteps)) {
            progressMap.remove(planId);
            return true;
        }
        return false;
    }

    public void markPlanFailed(String planId) {
        PlanProgress progress = progressMap.get(planId);
        if (progress != null) {
            progress.failed = true;
            progressMap.remove(planId);
        }
    }

    private static final class PlanProgress {
        private final Set<Integer> expectedSteps;
        private final Set<Integer> completedSteps = ConcurrentHashMap.newKeySet();
        private volatile boolean failed;

        private PlanProgress(Set<Integer> expectedSteps) {
            this.expectedSteps = Set.copyOf(expectedSteps);
        }
    }
}
