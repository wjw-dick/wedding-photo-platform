# 会员端 App（Flutter）

婚礼影像平台会员端，覆盖登录、订单、选片进度、交付下载、站内信与个人资料。

## 目录

```
lib/
  main.dart              应用入口与底部导航
  api.dart               HTTP 客户端与 Token 持久化
  pages/
    login_page.dart      手机号登录（生产接微信授权）
    home_page.dart       会员首页与订单概览
    orders_page.dart     订单列表
    order_detail_page.dart 订单详情、选片、合同、交付
    messages_page.dart   站内信
    profile_page.dart    资料、优惠券、退出
```

## 本地运行

本仓库只保留 Dart 源码（构建环境未安装 Flutter SDK）。在装有 Flutter 3.19+ 的机器上执行：

```bash
cd app
flutter create --platforms=android,ios .
flutter pub get
flutter run --dart-define=API_BASE=http://10.0.2.2:18090/api --dart-define=TENANT=demo
```

Android 模拟器访问宿主机使用 `10.0.2.2`；真机改为局域网 IP，并在 `AndroidManifest.xml` 允许明文 HTTP（仅开发环境）。

## 生产注意

- 登录改为微信开放平台 `unionId` 授权，后端 `/api/open/{tenant}/member/login`。
- 所有请求走 HTTPS，Token 存 `shared_preferences`，退出时清理。
- 交付文件使用后端签名 URL，不在客户端拼接对象存储路径。
