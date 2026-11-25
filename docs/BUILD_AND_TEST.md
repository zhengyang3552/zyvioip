# 构建与验证

本项目**不在本机构建**，所有构建、测试、lint、打包都在 GitHub Actions 完成
（`.github/workflows/ci.yml`）。本机只做代码编辑与提交。

## 触发方式

| 动作 | 结果 |
| --- | --- |
| push 到 `main` | 单元测试 + Debug APK + Lint |
| 打 `v*` tag | 单元测试 → 签名 Release APK → 校验签名方案 → 发布 GitHub Release |
| PR | 单元测试 + Debug APK + Lint |

## 发布一版的标准流程

```bash
git add -A
git commit -m "fix: ..."
git push origin main
git tag v1.0.3
git push origin v1.0.3
```

发布包路径：`https://github.com/zhengyang3552/zyvioip/releases/tag/v1.0.3`

同一个 Release 里会带两个文件（内容相同）：`zyvioip-v1.0.3-release.apk` 和 `zyvioip-latest.apk`。

## 装包前自查

CI 日志里必须出现：

```
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
```

以及 Step Summary 里的 `SHA-256` 等于 `docs/SIGNING.md` 中的固定值。
否则不要发这个包 —— 国产 ROM 上装不上。
