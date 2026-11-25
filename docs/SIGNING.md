## Release signing

The release workflow uses the shared ZY signing certificate so updates can overwrite previous installs.

Configure these repository Secrets in `zhengyang3552/zyvioip` → Settings → Secrets and variables → Actions:

- `KEYSTORE_BASE64` — base64 of the PKCS12 keystore.
- `KEYSTORE_PASSWORD` — keystore password.
- `KEY_ALIAS` — `zy_release`.
- `KEY_PASSWORD` — key password.

Expected certificate SHA-256 (verify in workflow summary after a release):

```text
52:C7:67:37:D2:2A:A3:E7:F2:BF:1A:C9:8E:9F:DB:5F:D4:77:60:34:61:22:64:E6:83:21:3C:44:D2:70:A1:B4
```

The workflow fails if release secrets are absent, so it never produces a randomly signed release APK. Debug builds work without secrets.
