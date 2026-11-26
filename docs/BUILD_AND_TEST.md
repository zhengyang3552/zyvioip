# 构建与验证

本项目**不在本机构建**，所有构建、测试、lint、打包都在 GitHub Actions 完成
（`.github/workflows/ci.yml`）。本机只做代码编辑与提交。

## 触发方式

| 动作 | 结果 |
| --- | --- |
| push 到 `main` | 单元测试 + Debug APK + Lint |
| push `v*` tag | 单元测试 + Debug APK + Lint（**不发布 Release**） |
| PR | 单元测试 + Debug APK + Lint |
| 手动 Run workflow + 勾选 `publish` | 额外执行 release job，创建 GitHub Release |

> **推 tag 不会自动发版。** tag 只用于标记代码节点与跑质量门。
> 需要发版时必须手动触发，见下方「发布一版」。

## 发布一版（手动）

1. 打开 Actions → `CI` → 右上角 **Run workflow**
2. 选择要发布的分支（通常是 `main`）
3. 填写输入项：
   - **publish**：勾选（不勾只跑质量门，不发版）
   - **tag**：版本号，例如 `v0.0.8`
4. 运行后 release job 会：
   - 构建签名 Release APK
   - 校验 v1+v2+v3 签名方案与证书指纹
   - 校验 lifecycle 依赖版本（防启动崩溃）
   - 校验 zipalign 对齐
   - 创建同名 tag（若不存在）并上传 `zyvioip-<version>.apk`

## 版本号规则

`VERSION_NAME` 按优先级解析：

1. 手动触发时填写的 `tag` 输入
2. tag 推送时的 `ref_name`
3. 都为空 → 回退 `app/build.gradle.kts` 里的默认值

`versionCode` 始终为 `100 + GITHUB_RUN_NUMBER`，单调递增，不会因降级装不上。

## 装包前自查

CI 日志里必须出现：

```
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
v1 签名文件齐全：MANIFEST.CERT.SF / CERT.RSA
lifecycle 版本校验通过
```

以及 Step Summary 里的 `SHA-256` 等于 `docs/SIGNING.md` 中的固定值。
否则不要发这个包。

## lifecycle 版本约束（重要）

`androidx.lifecycle` 整组锁定 **2.7.0**，不能升到 2.8.x。

原因：2.8.0 起 `lifecycle-runtime-compose` 把 `LocalLifecycleOwner` 迁到
`androidx.lifecycle.compose` 包下，需要 `compose-ui >= 1.7` 才会注入。
本项目 BOM 2024.06 的 `compose-ui` 是 1.6.8，升级后启动即崩：

```
IllegalStateException: CompositionLocal LocalLifecycleOwner not present
```

`androidx.lifecycle` 是 atomic group，只会改其中一个包是无效的，会被仲裁回高版本。
CI 的「校验 lifecycle 依赖版本」步骤会解包 APK 检查实际版本，不符直接失败。
