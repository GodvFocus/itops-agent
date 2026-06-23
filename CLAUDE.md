## 代码结构

- `config/`：Spring 配置类
- `controller/`：控制层与全局异常处理
- `dto/`：请求与响应数据结构
- `entity/`：数据库实体与枚举定义
- `interceptor/`：Spring MVC 拦截器
- `mapper/`：持久层接口，统一按 `MyBatis-Plus` 风格组织
- `service/`：业务服务接口
- `service/impl/`：业务服务实现
- `utils/`：通用工具与自定义异常

## 编码偏好

- 实体类优先使用 `Lombok` 降低模板代码噪音。
- 控制层保持轻量，业务逻辑统一下沉到 `service/impl/`。
- 状态流转、并发保护、审计持久化等核心逻辑统一放在服务层实现。
- 关键代码需要补充中文注释，重点说明“为什么这样做”，不要写成语法翻译。
- 新增后端模块时优先沿用现有分层结构，不随意发散目录层级。

## 技术栈约束

### Java 侧

- Java Web 框架统一使用 `Spring Boot`。
- 持久层统一使用 `MyBatis-Plus`，不引入 `Spring Data JPA`、`Hibernate`。
- 数据库统一使用 `MySQL`，不使用 `H2`。
- 数据库结构通过项目内显式 SQL 初始化脚本维护，不引入 `Flyway`。
- 并发更新统一基于 `MyBatis-Plus` 乐观锁能力处理。

### Python 侧

- 结构化数据校验统一使用 `Pydantic`。
- 测试统一使用 `pytest`。
- Agent Runtime 的节点输出契约、上下文对象与测试示例都应围绕 `Pydantic + pytest` 组织。

## 当前统一技术栈

- Java 21
- Spring Boot 3
- Spring Web MVC
- MyBatis-Plus
- MySQL
- Lombok
- Maven
- JUnit 5
- Python 3
- Pydantic
- pytest

## Git 约束

- 涉及 Git 操作时，提交信息统一使用中文。
- 提交时按模块拆分，避免把无关改动揉在一起。
- `git commit message` 中不要加入 `Co-Authored-By`。

## Python 运行环境

- 运行 Python 代码时使用 `D:/anaconda3/envs/lc/python.exe`。
- 不使用 `conda run` 方式执行项目内 Python 命令。
