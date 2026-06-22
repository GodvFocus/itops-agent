package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.entity.enums.UserRole;
import com.itops.itopsagent.service.TicketStateMachineService;
import com.itops.itopsagent.utils.exception.InvalidTicketStateTransitionException;
import com.itops.itopsagent.utils.exception.TicketTransitionForbiddenException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TicketStateMachineServiceImpl implements TicketStateMachineService {

    /** 每个状态允许流转到的目标状态集合。 */
    private final Map<TicketStatus, Set<TicketStatus>> allowedTransitions = new EnumMap<>(TicketStatus.class);

    public TicketStateMachineServiceImpl() {
        // 在构造阶段一次性注册完整状态图，避免规则散落在业务代码各处。
        register(TicketStatus.NEW, TicketStatus.TRIAGING, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.TRIAGING, TicketStatus.NEED_MORE_INFO, TicketStatus.PLANNING, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.NEED_MORE_INFO, TicketStatus.TRIAGING, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.PLANNING, TicketStatus.PLAN_VALIDATING, TicketStatus.MANUAL_TAKEOVER);
        register(
                TicketStatus.PLAN_VALIDATING,
                TicketStatus.EXECUTING,
                TicketStatus.WAITING_APPROVAL,
                TicketStatus.ESCALATED,
                TicketStatus.MANUAL_TAKEOVER);
        register(
                TicketStatus.WAITING_APPROVAL,
                TicketStatus.EXECUTING,
                TicketStatus.ESCALATED,
                TicketStatus.MANUAL_TAKEOVER);
        register(
                TicketStatus.EXECUTING,
                TicketStatus.WAITING_USER_CONFIRM,
                TicketStatus.FAILED,
                TicketStatus.ESCALATED,
                TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.WAITING_USER_CONFIRM, TicketStatus.RESOLVED, TicketStatus.TRIAGING, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.FAILED, TicketStatus.ESCALATED, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.ESCALATED, TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.MANUAL_TAKEOVER);
        register(TicketStatus.CLOSED);
    }

    @Override
    public void assertTransitionAllowed(TicketStatus currentStatus, TicketStatus targetStatus, UserRole actorRole) {
        Set<TicketStatus> targets = allowedTransitions.getOrDefault(currentStatus, EnumSet.noneOf(TicketStatus.class));
        // 先判断状态图是否合法，再判断当前角色是否有权执行该流转。
        if (!targets.contains(targetStatus)) {
            throw new InvalidTicketStateTransitionException(currentStatus, targetStatus, actorRole);
        }
        if (!isRoleAllowed(actorRole, currentStatus, targetStatus)) {
            throw new TicketTransitionForbiddenException(actorRole, currentStatus, targetStatus);
        }
    }

    private boolean isRoleAllowed(UserRole actorRole, TicketStatus currentStatus, TicketStatus targetStatus) {
        // ADMIN 保留人工兜底能力，但依然不能绕过状态图定义的边界。
        if (actorRole == UserRole.ADMIN) {
            return true;
        }
        if (targetStatus == TicketStatus.MANUAL_TAKEOVER) {
            return actorRole == UserRole.IT_ENGINEER;
        }
        return switch (actorRole) {
            case EMPLOYEE -> currentStatus == TicketStatus.WAITING_USER_CONFIRM
                    && (targetStatus == TicketStatus.RESOLVED || targetStatus == TicketStatus.TRIAGING);
            case IT_ENGINEER -> switch (currentStatus) {
                case NEW -> targetStatus == TicketStatus.TRIAGING;
                case TRIAGING -> targetStatus == TicketStatus.NEED_MORE_INFO || targetStatus == TicketStatus.PLANNING;
                case NEED_MORE_INFO -> targetStatus == TicketStatus.TRIAGING;
                case PLANNING -> targetStatus == TicketStatus.PLAN_VALIDATING;
                case PLAN_VALIDATING -> targetStatus == TicketStatus.EXECUTING
                        || targetStatus == TicketStatus.WAITING_APPROVAL
                        || targetStatus == TicketStatus.ESCALATED;
                case EXECUTING -> targetStatus == TicketStatus.WAITING_USER_CONFIRM
                        || targetStatus == TicketStatus.FAILED
                        || targetStatus == TicketStatus.ESCALATED;
                case RESOLVED -> targetStatus == TicketStatus.CLOSED;
                case FAILED -> targetStatus == TicketStatus.ESCALATED;
                default -> false;
            };
            case APPROVER -> currentStatus == TicketStatus.WAITING_APPROVAL
                    && (targetStatus == TicketStatus.EXECUTING || targetStatus == TicketStatus.ESCALATED);
            case ADMIN -> true;
        };
    }

    private void register(TicketStatus currentStatus, TicketStatus... targets) {
        Set<TicketStatus> targetSet = EnumSet.noneOf(TicketStatus.class);
        for (TicketStatus target : targets) {
            targetSet.add(target);
        }
        allowedTransitions.put(currentStatus, targetSet);
    }
}
