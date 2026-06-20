# Commit Message Template

使用以下格式：

```text
phase-{n}: <short summary>

- Implemented:
  - ...
- Tests:
  - ...
- Notes:
  - ...
```

示例：

```text
phase-1: implement ticket state machine skeleton

- Implemented:
  - Ticket create/list/detail APIs
  - Ticket status transition guard
  - Audit log on state changes
- Tests:
  - TicketStateMachineTest
  - TicketControllerTest
- Notes:
  - No Agent integration in this phase
```
