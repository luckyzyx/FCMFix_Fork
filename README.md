# FCMFix [![Build CI](https://github.com/luckyzyx/FCMFix_Fork/workflows/Build%20CI/badge.svg)](https://github.com/luckyzyx/FCMFix_Fork/actions)

仅替换Material3 UI和移除非COS Hook版本，仅供学习使用

推荐使用[原作者 kooritea](https://github.com/kooritea/fcmfix)的版本

### 介绍

让fcm/gcm唤醒未启动的应用进行发送通知

### 附加功能

- 阻止Android系统在应用停止时自动移除通知栏的通知
- ColorOS上动态解除来自fcm的自启动限制

### 作用域

- 勾选gms服务可以在无法按预期启动目标应用时代为发送一条提示通知
- 使用Timer间隔发送GCM进行心跳重连
- 但是会破坏play integrity legacy check

### 关于fcm

fcm是在Android中由google维护的一条介于google服务器与gms应用之间用于推送通知的长链接

一般的工作流程为应用服务器将消息发送到google服务器，google服务器将消息推送给gms应用

gms应用通过广播传递给应用，应用通过接收到的fcm消息决定是否发送通知和通知内容

其中gms通过fcm广播通知应用时，如果应用处于非运行状态，就会出现`Failed to broadcast to stopped app`

fcmfix主要就是解决这个问题。

### 已知问题

- 可能需要给予目标应用类似允许自启动的权限
