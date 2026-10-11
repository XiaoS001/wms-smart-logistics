/**
 * 提交信息规范：Conventional Commits。
 *
 * 允许的类型与含义：
 *   feat     新功能
 *   fix      缺陷修复
 *   docs     文档变更
 *   style    代码格式（不影响逻辑）
 *   refactor 重构（既非新功能也非修复）
 *   perf     性能优化
 *   test     测试相关
 *   build    构建系统或依赖变更
 *   ci       持续集成配置
 *   chore    其他杂项
 *   revert   回滚
 */
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'docs',
        'style',
        'refactor',
        'perf',
        'test',
        'build',
        'ci',
        'chore',
        'revert'
      ]
    ],
    'subject-case': [0],
    'subject-full-stop': [0],
    'header-max-length': [2, 'always', 100],
    'body-max-line-length': [0]
  }
}
