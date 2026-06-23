package com.itops.itopsagent.service.harness;

import java.util.Map;

public interface ToolGateway {

    Map<String, Object> execute(ToolExecutionTask task);
}
