## Release signing

The release workflow uses the shared ZY signing certificate so updates can overwrite previous installs.

Configure these repository Secrets in `zhengyang3552/zyvioip` → Settings → Secrets and variables → Actions:

- `KEYSTORE_BASE64` — base64 of the PKCS12 keystore.
- `KEYSTORE_PASSWORD` — keystore password.
- `KEY_ALIAS` — `zy_release`.
- `KEY_PASSWORD` — key password.

Expected certificate SHA-256 (verified automatically on every release):

```text
52:C7:67:37:D2:2A:A3:E7:F2:BF:1A:C9:8E:9F:DB:5F:D4:77:60:34:61:22:64:E6:83:21:3C:44:D2:70:A1:B4
```

## 签名方案：必须同时开启 v1 / v2 / v3

`app/build.gradle.kts` 的 `signingConfigs.release` 里显式打开了：

```kotlin
enableV1Signing = true
enableV2Signing = true
enableV3Signing = true
```

**不要删掉这三行。** AGP 在 `minSdk >= 24` 时默认只签 v2，而 MIUI / HyperOS、ColorOS、
OriginOS、EMUI 等国产 ROM 的包安装器仍会读取 v1（JAR）签名做预校验，只有 v2 的包会被
判定为「解析软件包时出现问题 / 无效的安装包」，直接装不上。

v1.0.0 ~ v1.0.2 就是踩了这个坑：包的 ZIP 结构、dex、证书全都正常，但 `apksigner verify`
显示 `Verified using v1 scheme (JAR signing): false`，因此在这些 ROM 上无法安装。

## CI 的强制校验

`release` job 在发布前会执行 `apksigner verify --verbose --print-certs`，并断言：

- v1 (JAR signing) 必须为 true
- v2 必须为 true
- v3 缺失只告警
- 证书 SHA-256 必须等于上面的固定值

任一条件不满足，CI 直接失败，不会产出发布包。

## versionCode

由 CI 注入 `BUILD_NUMBER`：`versionCode = 100 + GITHUB_RUN_NUMBER`，保证单调递增，
避免「版本降级」导致覆盖安装失败。本地无 `BUILD_NUMBER` 时回退到 `100`。
