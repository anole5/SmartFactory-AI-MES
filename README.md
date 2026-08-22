# SmartFactory-AI-MES

轻量制造执行系统（MES）+ AI 工厂知识库。

> 面向离散制造场景的学习/演示项目，第一版以 **AOC 55 英寸 4K 智能电视** 为 Demo 场景，
> 覆盖产品、物料、BOM、工艺路线、生产工单、派工、报工、质检、追溯、异常、生产看板与 AI 应用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5、MyBatis-Plus 3.5、MySQL 8 |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia |
| 部署 | Docker Compose（开发环境复用本机已有 MySQL 容器） |

## 目录结构

```text
SmartFactory-AI-MES
├── backend/     # Spring Boot 后端（模块化单体，包结构 com.smartfactory.mes）
├── frontend/    # Vue 3 前端
├── sql/         # 建库建表脚本与电视 Demo 种子数据
└── docs/        # 设计文档与方案（来源：Obsidian 代码与文档规划）
```

## 快速开始

（待 T7 补全：环境要求、数据库导入、后端启动、前端启动、演示步骤）

## 开发进度

- [x] 第 1 周：工程骨架 + 基础资料（产品/物料/BOM/工艺路线/工序/工位）
- [ ] 第 2 周：生产执行（工单/下发/工序任务/派工/报工）
- [ ] 第 3 周：质量、追溯与看板
- [ ] 第 4 周：AI 应用与项目包装
